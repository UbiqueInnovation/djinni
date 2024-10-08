// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from set.djinni

package com.dropbox.djinni.test;

import java.util.HashSet;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class SetRecord implements android.os.Parcelable {


    /*package*/ final HashSet<String> mSset;

    /*package*/ final HashSet<Integer> mIset;

    public SetRecord(
            @Nonnull HashSet<String> sset,
            @Nonnull HashSet<Integer> iset) {
        this.mSset = sset;
        this.mIset = iset;
    }

    @Nonnull
    public HashSet<String> getSset() {
        return mSset;
    }

    @Nonnull
    public HashSet<Integer> getIset() {
        return mIset;
    }

    @Override
    public String toString() {
        return "SetRecord{" +
                "mSset=" + mSset +
                "," + "mIset=" + mIset +
        "}";
    }


    public static final android.os.Parcelable.Creator<SetRecord> CREATOR
        = new android.os.Parcelable.Creator<SetRecord>() {
        @Override
        public SetRecord createFromParcel(android.os.Parcel in) {
            return new SetRecord(in);
        }

        @Override
        public SetRecord[] newArray(int size) {
            return new SetRecord[size];
        }
    };

    public SetRecord(android.os.Parcel in) {
        ArrayList<String> mSsetTemp = new ArrayList<String>();
        in.readList(mSsetTemp, getClass().getClassLoader());
        this.mSset = new HashSet<String>(mSsetTemp);
        ArrayList<Integer> mIsetTemp = new ArrayList<Integer>();
        in.readList(mIsetTemp, getClass().getClassLoader());
        this.mIset = new HashSet<Integer>(mIsetTemp);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeList(new ArrayList<String>(this.mSset));
        out.writeList(new ArrayList<Integer>(this.mIset));
    }

}
