import Foundation

/// Producer side of the native Swift future. Completion is synchronized by Future.
public final class DJPromise<Value> {
    private let future: DJFuture<Value>
    private let complete: (Result<Value, DjinniError>) -> Void

    public init() {
        var completion: ((Result<Value, DjinniError>) -> Void)!
        future = DJFuture { completion = $0 }
        complete = completion
    }

    deinit {
        future.resolve(.failure(DjinniError("DJPromise was deallocated before setting a result")))
    }

    public func setValue(_ value: Value) { complete(.success(value)) }
    public func setException(_ error: DjinniError) { complete(.failure(error)) }
    public func getFuture() -> DJFuture<Value> { future }
}
