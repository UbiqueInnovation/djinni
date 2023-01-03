// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from multiple_inheritance.djinni

package com.dropbox.djinni.test;

import com.snapchat.djinni.NativeObjectManager;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/** Used for C++ multiple inheritance tests */
public abstract class ReturnOne {
    public abstract byte returnOne();

    @CheckForNull
    public static native ReturnOne getInstance();

    public static final class CppProxy extends ReturnOne
    {
        private final long nativeRef;
        private final AtomicBoolean destroyed = new AtomicBoolean(false);

        private CppProxy(long nativeRef)
        {
            if (nativeRef == 0) throw new RuntimeException("nativeRef is zero");
            this.nativeRef = nativeRef;
            NativeObjectManager.register(this, nativeRef);
        }
        public static native void nativeDestroy(long nativeRef);

        @Override
        public byte returnOne()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_returnOne(this.nativeRef);
        }
        private native byte native_returnOne(long _nativeRef);
    }
}
