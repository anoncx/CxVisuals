package me.cxiy.cxvisuals.modules.avatar.anim;

public final class Affine {
  private Affine() {}

  public static float[] identity(float[] out) {
    return identity(out, 0);
  }

  public static float[] identity(float[] out, int o) {
    for (int i = 0; i < 16; i++) {
      out[o + i] = (i % 5 == 0) ? 1.0F : 0.0F;
    }
    return out;
  }

  public static float[] compose(
      float tx,
      float ty,
      float tz,
      float qx,
      float qy,
      float qz,
      float qw,
      float sx,
      float sy,
      float sz,
      float[] out,
      int o) {
    float xx = qx * qx;
    float yy = qy * qy;
    float zz = qz * qz;
    float xy = qx * qy;
    float xz = qx * qz;
    float yz = qy * qz;
    float wx = qw * qx;
    float wy = qw * qy;
    float wz = qw * qz;
    out[o] = (1.0F - 2.0F * (yy + zz)) * sx;
    out[o + 1] = 2.0F * (xy + wz) * sx;
    out[o + 2] = 2.0F * (xz - wy) * sx;
    out[o + 3] = 0.0F;
    out[o + 4] = 2.0F * (xy - wz) * sy;
    out[o + 5] = (1.0F - 2.0F * (xx + zz)) * sy;
    out[o + 6] = 2.0F * (yz + wx) * sy;
    out[o + 7] = 0.0F;
    out[o + 8] = 2.0F * (xz + wy) * sz;
    out[o + 9] = 2.0F * (yz - wx) * sz;
    out[o + 10] = (1.0F - 2.0F * (xx + yy)) * sz;
    out[o + 11] = 0.0F;
    out[o + 12] = tx;
    out[o + 13] = ty;
    out[o + 14] = tz;
    out[o + 15] = 1.0F;
    return out;
  }

  public static float[] multiply(float[] a, int ao, float[] b, int bo, float[] out, int o) {
    float a00 = a[ao];
    float a01 = a[ao + 1];
    float a02 = a[ao + 2];
    float a03 = a[ao + 3];
    float a10 = a[ao + 4];
    float a11 = a[ao + 5];
    float a12 = a[ao + 6];
    float a13 = a[ao + 7];
    float a20 = a[ao + 8];
    float a21 = a[ao + 9];
    float a22 = a[ao + 10];
    float a23 = a[ao + 11];
    float a30 = a[ao + 12];
    float a31 = a[ao + 13];
    float a32 = a[ao + 14];
    float a33 = a[ao + 15];
    for (int c = 0; c < 4; c++) {
      float b0 = b[bo + c * 4];
      float b1 = b[bo + c * 4 + 1];
      float b2 = b[bo + c * 4 + 2];
      float b3 = b[bo + c * 4 + 3];
      out[o + c * 4] = a00 * b0 + a10 * b1 + a20 * b2 + a30 * b3;
      out[o + c * 4 + 1] = a01 * b0 + a11 * b1 + a21 * b2 + a31 * b3;
      out[o + c * 4 + 2] = a02 * b0 + a12 * b1 + a22 * b2 + a32 * b3;
      out[o + c * 4 + 3] = a03 * b0 + a13 * b1 + a23 * b2 + a33 * b3;
    }
    return out;
  }

  public static float[] invert(float[] m, float[] out) {
    float a00 = m[0];
    float a01 = m[1];
    float a02 = m[2];
    float a03 = m[3];
    float a10 = m[4];
    float a11 = m[5];
    float a12 = m[6];
    float a13 = m[7];
    float a20 = m[8];
    float a21 = m[9];
    float a22 = m[10];
    float a23 = m[11];
    float a30 = m[12];
    float a31 = m[13];
    float a32 = m[14];
    float a33 = m[15];
    float b00 = a00 * a11 - a01 * a10;
    float b01 = a00 * a12 - a02 * a10;
    float b02 = a00 * a13 - a03 * a10;
    float b03 = a01 * a12 - a02 * a11;
    float b04 = a01 * a13 - a03 * a11;
    float b05 = a02 * a13 - a03 * a12;
    float b06 = a20 * a31 - a21 * a30;
    float b07 = a20 * a32 - a22 * a30;
    float b08 = a20 * a33 - a23 * a30;
    float b09 = a21 * a32 - a22 * a31;
    float b10 = a21 * a33 - a23 * a31;
    float b11 = a22 * a33 - a23 * a32;
    float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
    if (Math.abs(det) < 1e-20F) {
      return identity(out);
    }
    float inv = 1.0F / det;
    out[0] = (a11 * b11 - a12 * b10 + a13 * b09) * inv;
    out[1] = (a02 * b10 - a01 * b11 - a03 * b09) * inv;
    out[2] = (a31 * b05 - a32 * b04 + a33 * b03) * inv;
    out[3] = (a22 * b04 - a21 * b05 - a23 * b03) * inv;
    out[4] = (a12 * b08 - a10 * b11 - a13 * b07) * inv;
    out[5] = (a00 * b11 - a02 * b08 + a03 * b07) * inv;
    out[6] = (a32 * b02 - a30 * b05 - a33 * b01) * inv;
    out[7] = (a20 * b05 - a22 * b02 + a23 * b01) * inv;
    out[8] = (a10 * b10 - a11 * b08 + a13 * b06) * inv;
    out[9] = (a01 * b08 - a00 * b10 - a03 * b06) * inv;
    out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * inv;
    out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * inv;
    out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * inv;
    out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * inv;
    out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * inv;
    out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * inv;
    return out;
  }

  public static float[] point(float[] m, int o, float x, float y, float z, float[] out) {
    out[0] = m[o] * x + m[o + 4] * y + m[o + 8] * z + m[o + 12];
    out[1] = m[o + 1] * x + m[o + 5] * y + m[o + 9] * z + m[o + 13];
    out[2] = m[o + 2] * x + m[o + 6] * y + m[o + 10] * z + m[o + 14];
    return out;
  }

  public static float[] direction(float[] m, int o, float x, float y, float z, float[] out) {
    out[0] = m[o] * x + m[o + 4] * y + m[o + 8] * z;
    out[1] = m[o + 1] * x + m[o + 5] * y + m[o + 9] * z;
    out[2] = m[o + 2] * x + m[o + 6] * y + m[o + 10] * z;
    return out;
  }

  public static float[] normalMatrix(float[] m, float[] out) {
    float[] inverse = invert(m, new float[16]);
    for (int r = 0; r < 4; r++) {
      for (int c = 0; c < 4; c++) {
        out[c * 4 + r] = inverse[r * 4 + c];
      }
    }
    out[3] = 0.0F;
    out[7] = 0.0F;
    out[11] = 0.0F;
    out[12] = 0.0F;
    out[13] = 0.0F;
    out[14] = 0.0F;
    out[15] = 1.0F;
    return out;
  }

  public static float determinant3(float[] m) {
    return m[0] * (m[5] * m[10] - m[9] * m[6])
        - m[4] * (m[1] * m[10] - m[9] * m[2])
        + m[8] * (m[1] * m[6] - m[5] * m[2]);
  }

  public static float[] rotationX(float radians, float[] out) {
    identity(out);
    float c = (float) Math.cos(radians);
    float s = (float) Math.sin(radians);
    out[5] = c;
    out[6] = s;
    out[9] = -s;
    out[10] = c;
    return out;
  }

  public static float[] rotationY(float radians, float[] out) {
    identity(out);
    float c = (float) Math.cos(radians);
    float s = (float) Math.sin(radians);
    out[0] = c;
    out[2] = -s;
    out[8] = s;
    out[10] = c;
    return out;
  }

  public static void slerp(float[] a, int ao, float[] b, int bo, float t, float[] out, int o) {
    float ax = a[ao];
    float ay = a[ao + 1];
    float az = a[ao + 2];
    float aw = a[ao + 3];
    float bx = b[bo];
    float by = b[bo + 1];
    float bz = b[bo + 2];
    float bw = b[bo + 3];
    float dot = ax * bx + ay * by + az * bz + aw * bw;
    if (dot < 0.0F) {
      dot = -dot;
      bx = -bx;
      by = -by;
      bz = -bz;
      bw = -bw;
    }
    float wa;
    float wb;
    if (dot > 0.9995F) {
      wa = 1.0F - t;
      wb = t;
    } else {
      float theta = (float) Math.acos(dot);
      float sin = (float) Math.sin(theta);
      wa = (float) Math.sin((1.0F - t) * theta) / sin;
      wb = (float) Math.sin(t * theta) / sin;
    }
    out[o] = ax * wa + bx * wb;
    out[o + 1] = ay * wa + by * wb;
    out[o + 2] = az * wa + bz * wb;
    out[o + 3] = aw * wa + bw * wb;
    normalize4(out, o);
  }

  public static void normalize4(float[] q, int o) {
    float length =
        (float)
            Math.sqrt(
                q[o] * q[o] + q[o + 1] * q[o + 1] + q[o + 2] * q[o + 2] + q[o + 3] * q[o + 3]);
    if (length < 1e-12F) {
      q[o] = 0.0F;
      q[o + 1] = 0.0F;
      q[o + 2] = 0.0F;
      q[o + 3] = 1.0F;
      return;
    }
    float inv = 1.0F / length;
    q[o] *= inv;
    q[o + 1] *= inv;
    q[o + 2] *= inv;
    q[o + 3] *= inv;
  }

  public static void decompose(float[] m, float[] t, float[] r, float[] s) {
    t[0] = m[12];
    t[1] = m[13];
    t[2] = m[14];
    float sx = (float) Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
    float sy = (float) Math.sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]);
    float sz = (float) Math.sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]);
    if (determinant3(m) < 0.0F) {
      sx = -sx;
    }
    s[0] = sx;
    s[1] = sy;
    s[2] = sz;
    float m00 = sx == 0.0F ? 1.0F : m[0] / sx;
    float m01 = sx == 0.0F ? 0.0F : m[1] / sx;
    float m02 = sx == 0.0F ? 0.0F : m[2] / sx;
    float m10 = sy == 0.0F ? 0.0F : m[4] / sy;
    float m11 = sy == 0.0F ? 1.0F : m[5] / sy;
    float m12 = sy == 0.0F ? 0.0F : m[6] / sy;
    float m20 = sz == 0.0F ? 0.0F : m[8] / sz;
    float m21 = sz == 0.0F ? 0.0F : m[9] / sz;
    float m22 = sz == 0.0F ? 1.0F : m[10] / sz;
    float trace = m00 + m11 + m22;
    if (trace > 0.0F) {
      float k = (float) Math.sqrt(trace + 1.0F) * 2.0F;
      r[3] = 0.25F * k;
      r[0] = (m12 - m21) / k;
      r[1] = (m20 - m02) / k;
      r[2] = (m01 - m10) / k;
    } else if (m00 > m11 && m00 > m22) {
      float k = (float) Math.sqrt(1.0F + m00 - m11 - m22) * 2.0F;
      r[3] = (m12 - m21) / k;
      r[0] = 0.25F * k;
      r[1] = (m10 + m01) / k;
      r[2] = (m20 + m02) / k;
    } else if (m11 > m22) {
      float k = (float) Math.sqrt(1.0F + m11 - m00 - m22) * 2.0F;
      r[3] = (m20 - m02) / k;
      r[0] = (m10 + m01) / k;
      r[1] = 0.25F * k;
      r[2] = (m21 + m12) / k;
    } else {
      float k = (float) Math.sqrt(1.0F + m22 - m00 - m11) * 2.0F;
      r[3] = (m01 - m10) / k;
      r[0] = (m20 + m02) / k;
      r[1] = (m21 + m12) / k;
      r[2] = 0.25F * k;
    }
    normalize4(r, 0);
  }
}
