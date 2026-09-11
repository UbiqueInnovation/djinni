import DjinniSupportCxx
import Foundation

// Synchronous callback dispatch. Each generated method supplies its own typed,
// stack-owned C++ payload; the raw pointer must never escape the callback.
public typealias Vtbl<T> = [(T, UnsafeMutableRawPointer?) throws -> Void]

// Type erased interface for ProtocolWrapperContext. We don't have the type
// parameter T for ProtocolWrapperContext inside dispatcherProtocalCall (called
// by C++ code)
protocol GenericProtocolWrapperContext: AnyObject {
    func dispatch(idx: Int32, params: UnsafeMutableRawPointer?, ret: UnsafeMutablePointer<djinni.swift.AnyValue>?) -> Void
    func getInst() -> AnyObject
    var interfaceType: ObjectIdentifier { get }
}

// The bridge between C++ caller and Swift protocol. We store
// - The object instance (that conforms to our protocol)
// - The dispatch table for each callable method in the protocol
final class ProtocolWrapperContext<T>: GenericProtocolWrapperContext {
    let inst: T
    let vtbl: Vtbl<T>
    var interfaceType: ObjectIdentifier { ObjectIdentifier(T.self) }
    init(inst: T, vtbl: Vtbl<T>) {
        self.inst = inst
        self.vtbl = vtbl
    }
    func dispatch(idx: Int32, params: UnsafeMutableRawPointer?, ret: UnsafeMutablePointer<djinni.swift.AnyValue>?) -> Void {
        // No Swift error will cross the language boundary. They are converted
        // to `ErrorValue`s which will be translated into C++ exceptions on the
        // other side.
        do {
            try vtbl[Int(idx)](inst, params)
        } catch let error as DjinniError {
            djinni.swift.setErrorValue(ret, error.wrapped)
        } catch {
            djinni.swift.setErrorMessage(ret, std.string(String(describing: error)))
        }
    }
    func getInst() -> AnyObject {
        return inst as AnyObject
    }
}

// This function has a C++ friendly signature. It is passed to C++ and will be
// used by proxy objects to dispatch protocol calls.
public func dispatcherProtocalCall(
  ptr: UnsafeMutableRawPointer?, // The Swift protocol wrapper object as an opaque pointer
  idx: Int32,                    // The method index (starting from 0)
  params: UnsafeMutableRawPointer?, // Borrowed typed payload from C++ caller
  ret: UnsafeMutablePointer<djinni.swift.AnyValue>?)  // Error channel; successful results are stored in the payload
  -> Void {
    guard let pctx = ptr else { return }
    let ctx = Unmanaged<AnyObject>.fromOpaque(pctx).takeUnretainedValue() as! GenericProtocolWrapperContext
    if (idx >= 0) {
        // Dynamic dispatch on the vtbl.  We use `takeUnretainedValue` here
        // because we want to keep the wrapper object alive inside the C++ side
        // proxy (inherits ProtocolWrapper).
        ctx.dispatch(idx: idx, params: params, ret: ret)
    } else {
        // If the index is negative, release and destroy the context.  We do
        // this when the C++ side proxy (ProtocolWrapper) is destroyed.
        let key = SwiftProxyKey(object: ObjectIdentifier(ctx.getInst()), interface: ctx.interfaceType)
        let cache = ProxyCache.shared
        cache.lock.lock()
        // An expired weak proxy may already have been replaced by another thread.
        if cache.swift[key]?.context == pctx {
            cache.swift.removeValue(forKey: key)
        }
        cache.lock.unlock()
        Unmanaged<AnyObject>.fromOpaque(pctx).release()
    }
}

// Base class for a Swift callable proxy of a C++ object
open class CppProxy {
    // Stores a C++ interface. A C++ interface value is a double pointer:
    // 1. A shared_ptr<> that keeps the C++ implementation object alive
    // 2. A shared_ptr<> to a ProtocolWrapper that facilitates dispatching
    public let inst: djinni.swift.AnyValue
    public init(inst: djinni.swift.AnyValue) {
        self.inst = inst
    }
    deinit {
        forgetCppProxy(inst)
    }
}

private final class WeakSwiftObject {
    weak var value: AnyObject?
    init(_ value: AnyObject) { self.value = value }
}

private struct SwiftProxyKey: Hashable {
    let object: ObjectIdentifier
    let interface: ObjectIdentifier
}

private struct WeakNativeProxy {
    let value: djinni.swift.WeakSwiftProxy
    let context: UnsafeMutableRawPointer?
}

private final class ProxyCache {
    static let shared = ProxyCache()
    // ponytail: serialize interface conversion; shard only if contention is measured.
    // Destruction of a temporary native/Swift reference can reenter cache cleanup.
    let lock = NSRecursiveLock()
    var cpp: [UnsafeRawPointer: [ObjectIdentifier: WeakSwiftObject]] = [:]
    var swift: [SwiftProxyKey: WeakNativeProxy] = [:]
}

// Remove dead entries only: another thread may have replaced a dying wrapper.
public func forgetCppProxy(_ handle: djinni.swift.AnyValue) {
    withUnsafePointer(to: handle) { p in
        guard let pointer = djinni.swift.getInterfaceInfo(p).cppPointer else { return }
        let cache = ProxyCache.shared
        cache.lock.lock()
        defer { cache.lock.unlock() }
        if let entries = cache.cpp[pointer] {
            for (type, entry) in entries where entry.value == nil {
                cache.cpp[pointer]?.removeValue(forKey: type)
            }
            if cache.cpp[pointer]?.isEmpty == true {
                cache.cpp.removeValue(forKey: pointer)
            }
        }
    }
}

public func cppInterfaceToSwift<I>(_ c: djinni.swift.AnyValue,
                                   _ newProxyFunc: ()->I) -> I {
    return withUnsafePointer(to: c) { p in
        let info = djinni.swift.getInterfaceInfo(p)
        // The native handle owns the context for the duration of this access.
        if let pctx = info.ctxPointer {
            let ctx = Unmanaged<AnyObject>.fromOpaque(pctx).takeUnretainedValue() as! GenericProtocolWrapperContext
            return ctx.getInst() as! I
        }
        let cache = ProxyCache.shared
        let type = ObjectIdentifier(I.self)
        cache.lock.lock()
        defer { cache.lock.unlock() }
        // Swift weak loads atomically acquire a strong reference; raw unretained
        // pointers cannot safely race with the previous wrapper's destruction.
        if let proxy = cache.cpp[info.cppPointer]?[type]?.value {
            return proxy as! I
        }
        let proxy = newProxyFunc()
        cache.cpp[info.cppPointer, default: [:]][type] = WeakSwiftObject(proxy as AnyObject)
        return proxy
    }
}

public func swiftInterfaceToCpp<I>(_ s: I,
                                   _ newProxyFunc: ()->djinni.swift.AnyValue) -> djinni.swift.AnyValue {
    if let cppproxy = s as? CppProxy {
        return cppproxy.inst
    }
    let key = SwiftProxyKey(object: ObjectIdentifier(s as AnyObject), interface: ObjectIdentifier(I.self))
    let cache = ProxyCache.shared
    cache.lock.lock()
    defer { cache.lock.unlock() }
    if let entry = cache.swift[key] {
        var proxy = djinni.swift.strongify(entry.value)
        if !djinni.swift.isVoidValue(&proxy) { return proxy }
    }
    let proxy = newProxyFunc()
    let context = withUnsafePointer(to: proxy) { djinni.swift.getInterfaceInfo($0).ctxPointer }
    cache.swift[key] = WeakNativeProxy(value: djinni.swift.weakify(proxy), context: context)
    return proxy
}

// Shortcut function to create a Swift protocol wrapper context and return its
// *Retained* pointer. The returned pointer is owned by the C++ side
// ProtocolWrapper, and released in ProtocolWrapper::~ProtocolWrapper() by
// sending a dispatch request with index -1.
public func ctxPtr<I> (_ s: I, _ vtbl: Vtbl<I>) -> UnsafeMutableRawPointer {
    let ctx = ProtocolWrapperContext(inst: s, vtbl: vtbl)
    return Unmanaged.passRetained(ctx).toOpaque()
}

// Wraps C++ ErrorValue as a Swift throwable Error
public class DjinniError: Error {
    var wrapped: djinni.swift.ErrorValue
    init(_ wrapped: djinni.swift.ErrorValue) {
        self.wrapped = wrapped
    }
    public init(_ msg: String) {
        self.wrapped = djinni.swift.ErrorValue(std.string(msg))
    }
    public var errorMessage: String { return String(wrapped.msg) }
}

// Called by stubs to convert C++ exceptions stored as ErrorValue to Swift errors
public func handleCppErrors(_ ret: UnsafePointer<djinni.swift.AnyValue>) throws {
    if (djinni.swift.isError(ret)) {
        throw DjinniError(djinni.swift.getError(ret))
    }
}
