// AUTOGENERATED FILE - DO NOT MODIFY!
// This file was generated by Djinni from primtypes.djinni

package com.dropbox.djinni.test;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class AssortedPrimitives implements android.os.Parcelable {


    /*package*/ final boolean mB;

    /*package*/ final byte mEight;

    /*package*/ final short mSixteen;

    /*package*/ final int mThirtytwo;

    /*package*/ final long mSixtyfour;

    /*package*/ final float mFthirtytwo;

    /*package*/ final double mFsixtyfour;

    /*package*/ final Boolean mOB;

    /*package*/ final Byte mOEight;

    /*package*/ final Short mOSixteen;

    /*package*/ final Integer mOThirtytwo;

    /*package*/ final Long mOSixtyfour;

    /*package*/ final Float mOFthirtytwo;

    /*package*/ final Double mOFsixtyfour;

    public AssortedPrimitives(
            boolean b,
            byte eight,
            short sixteen,
            int thirtytwo,
            long sixtyfour,
            float fthirtytwo,
            double fsixtyfour,
            @CheckForNull Boolean oB,
            @CheckForNull Byte oEight,
            @CheckForNull Short oSixteen,
            @CheckForNull Integer oThirtytwo,
            @CheckForNull Long oSixtyfour,
            @CheckForNull Float oFthirtytwo,
            @CheckForNull Double oFsixtyfour) {
        this.mB = b;
        this.mEight = eight;
        this.mSixteen = sixteen;
        this.mThirtytwo = thirtytwo;
        this.mSixtyfour = sixtyfour;
        this.mFthirtytwo = fthirtytwo;
        this.mFsixtyfour = fsixtyfour;
        this.mOB = oB;
        this.mOEight = oEight;
        this.mOSixteen = oSixteen;
        this.mOThirtytwo = oThirtytwo;
        this.mOSixtyfour = oSixtyfour;
        this.mOFthirtytwo = oFthirtytwo;
        this.mOFsixtyfour = oFsixtyfour;
    }

    public boolean getB() {
        return mB;
    }

    public byte getEight() {
        return mEight;
    }

    public short getSixteen() {
        return mSixteen;
    }

    public int getThirtytwo() {
        return mThirtytwo;
    }

    public long getSixtyfour() {
        return mSixtyfour;
    }

    public float getFthirtytwo() {
        return mFthirtytwo;
    }

    public double getFsixtyfour() {
        return mFsixtyfour;
    }

    @CheckForNull
    public Boolean getOB() {
        return mOB;
    }

    @CheckForNull
    public Byte getOEight() {
        return mOEight;
    }

    @CheckForNull
    public Short getOSixteen() {
        return mOSixteen;
    }

    @CheckForNull
    public Integer getOThirtytwo() {
        return mOThirtytwo;
    }

    @CheckForNull
    public Long getOSixtyfour() {
        return mOSixtyfour;
    }

    @CheckForNull
    public Float getOFthirtytwo() {
        return mOFthirtytwo;
    }

    @CheckForNull
    public Double getOFsixtyfour() {
        return mOFsixtyfour;
    }

    @Override
    public boolean equals(@CheckForNull Object obj) {
        if (!(obj instanceof AssortedPrimitives)) {
            return false;
        }
        AssortedPrimitives other = (AssortedPrimitives) obj;
        return this.mB == other.mB &&
                this.mEight == other.mEight &&
                this.mSixteen == other.mSixteen &&
                this.mThirtytwo == other.mThirtytwo &&
                this.mSixtyfour == other.mSixtyfour &&
                this.mFthirtytwo == other.mFthirtytwo &&
                this.mFsixtyfour == other.mFsixtyfour &&
                ((this.mOB == null && other.mOB == null) || (this.mOB != null && this.mOB.equals(other.mOB))) &&
                ((this.mOEight == null && other.mOEight == null) || (this.mOEight != null && this.mOEight.equals(other.mOEight))) &&
                ((this.mOSixteen == null && other.mOSixteen == null) || (this.mOSixteen != null && this.mOSixteen.equals(other.mOSixteen))) &&
                ((this.mOThirtytwo == null && other.mOThirtytwo == null) || (this.mOThirtytwo != null && this.mOThirtytwo.equals(other.mOThirtytwo))) &&
                ((this.mOSixtyfour == null && other.mOSixtyfour == null) || (this.mOSixtyfour != null && this.mOSixtyfour.equals(other.mOSixtyfour))) &&
                ((this.mOFthirtytwo == null && other.mOFthirtytwo == null) || (this.mOFthirtytwo != null && this.mOFthirtytwo.equals(other.mOFthirtytwo))) &&
                ((this.mOFsixtyfour == null && other.mOFsixtyfour == null) || (this.mOFsixtyfour != null && this.mOFsixtyfour.equals(other.mOFsixtyfour)));
    }

    @Override
    public int hashCode() {
        // Pick an arbitrary non-zero starting value
        int hashCode = 17;
        hashCode = hashCode * 31 + (mB ? 1 : 0);
        hashCode = hashCode * 31 + mEight;
        hashCode = hashCode * 31 + mSixteen;
        hashCode = hashCode * 31 + mThirtytwo;
        hashCode = hashCode * 31 + ((int) (mSixtyfour ^ (mSixtyfour >>> 32)));
        hashCode = hashCode * 31 + Float.floatToIntBits(mFthirtytwo);
        hashCode = hashCode * 31 + ((int) (Double.doubleToLongBits(mFsixtyfour) ^ (Double.doubleToLongBits(mFsixtyfour) >>> 32)));
        hashCode = hashCode * 31 + (mOB == null ? 0 : mOB.hashCode());
        hashCode = hashCode * 31 + (mOEight == null ? 0 : mOEight.hashCode());
        hashCode = hashCode * 31 + (mOSixteen == null ? 0 : mOSixteen.hashCode());
        hashCode = hashCode * 31 + (mOThirtytwo == null ? 0 : mOThirtytwo.hashCode());
        hashCode = hashCode * 31 + (mOSixtyfour == null ? 0 : mOSixtyfour.hashCode());
        hashCode = hashCode * 31 + (mOFthirtytwo == null ? 0 : mOFthirtytwo.hashCode());
        hashCode = hashCode * 31 + (mOFsixtyfour == null ? 0 : mOFsixtyfour.hashCode());
        return hashCode;
    }

    @Override
    public String toString() {
        return "AssortedPrimitives{" +
                "mB=" + mB +
                "," + "mEight=" + mEight +
                "," + "mSixteen=" + mSixteen +
                "," + "mThirtytwo=" + mThirtytwo +
                "," + "mSixtyfour=" + mSixtyfour +
                "," + "mFthirtytwo=" + mFthirtytwo +
                "," + "mFsixtyfour=" + mFsixtyfour +
                "," + "mOB=" + mOB +
                "," + "mOEight=" + mOEight +
                "," + "mOSixteen=" + mOSixteen +
                "," + "mOThirtytwo=" + mOThirtytwo +
                "," + "mOSixtyfour=" + mOSixtyfour +
                "," + "mOFthirtytwo=" + mOFthirtytwo +
                "," + "mOFsixtyfour=" + mOFsixtyfour +
        "}";
    }


    public static final android.os.Parcelable.Creator<AssortedPrimitives> CREATOR
        = new android.os.Parcelable.Creator<AssortedPrimitives>() {
        @Override
        public AssortedPrimitives createFromParcel(android.os.Parcel in) {
            return new AssortedPrimitives(in);
        }

        @Override
        public AssortedPrimitives[] newArray(int size) {
            return new AssortedPrimitives[size];
        }
    };

    public AssortedPrimitives(android.os.Parcel in) {
        this.mB = in.readByte() != 0;
        this.mEight = in.readByte();
        this.mSixteen = (short)in.readInt();
        this.mThirtytwo = in.readInt();
        this.mSixtyfour = in.readLong();
        this.mFthirtytwo = in.readFloat();
        this.mFsixtyfour = in.readDouble();
        if (in.readByte() == 0) {
            this.mOB = null;
        } else {
            this.mOB = in.readByte() != 0;
        }
        if (in.readByte() == 0) {
            this.mOEight = null;
        } else {
            this.mOEight = in.readByte();
        }
        if (in.readByte() == 0) {
            this.mOSixteen = null;
        } else {
            this.mOSixteen = (short)in.readInt();
        }
        if (in.readByte() == 0) {
            this.mOThirtytwo = null;
        } else {
            this.mOThirtytwo = in.readInt();
        }
        if (in.readByte() == 0) {
            this.mOSixtyfour = null;
        } else {
            this.mOSixtyfour = in.readLong();
        }
        if (in.readByte() == 0) {
            this.mOFthirtytwo = null;
        } else {
            this.mOFthirtytwo = in.readFloat();
        }
        if (in.readByte() == 0) {
            this.mOFsixtyfour = null;
        } else {
            this.mOFsixtyfour = in.readDouble();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeByte(this.mB ? (byte)1 : 0);
        out.writeByte(this.mEight);
        out.writeInt(this.mSixteen);
        out.writeInt(this.mThirtytwo);
        out.writeLong(this.mSixtyfour);
        out.writeFloat(this.mFthirtytwo);
        out.writeDouble(this.mFsixtyfour);
        if (this.mOB != null) {
            out.writeByte((byte)1);
            out.writeByte(this.mOB ? (byte)1 : 0);
        } else {
            out.writeByte((byte)0);
        }
        if (this.mOEight != null) {
            out.writeByte((byte)1);
            out.writeByte(this.mOEight);
        } else {
            out.writeByte((byte)0);
        }
        if (this.mOSixteen != null) {
            out.writeByte((byte)1);
            out.writeInt(this.mOSixteen);
        } else {
            out.writeByte((byte)0);
        }
        if (this.mOThirtytwo != null) {
            out.writeByte((byte)1);
            out.writeInt(this.mOThirtytwo);
        } else {
            out.writeByte((byte)0);
        }
        if (this.mOSixtyfour != null) {
            out.writeByte((byte)1);
            out.writeLong(this.mOSixtyfour);
        } else {
            out.writeByte((byte)0);
        }
        if (this.mOFthirtytwo != null) {
            out.writeByte((byte)1);
            out.writeFloat(this.mOFthirtytwo);
        } else {
            out.writeByte((byte)0);
        }
        if (this.mOFsixtyfour != null) {
            out.writeByte((byte)1);
            out.writeDouble(this.mOFsixtyfour);
        } else {
            out.writeByte((byte)0);
        }
    }

}
