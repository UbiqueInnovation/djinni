// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from proto.djinni

package com.dropbox.djinni.test;

import djinni.test2.Test2.PersistingState;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class RecordWithEmbeddedCppProto implements java.io.Serializable {


    /*package*/ final PersistingState mState;

    public RecordWithEmbeddedCppProto(
            @Nonnull PersistingState state) {
        this.mState = state;
    }

    @Nonnull
    public PersistingState getState() {
        return mState;
    }

    @Override
    public String toString() {
        return "RecordWithEmbeddedCppProto{" +
                "mState=" + mState +
        "}";
    }

}
