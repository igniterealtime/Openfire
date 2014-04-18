// Copyright 2012 Google Inc. All Rights Reserved.
// Author: frkoenig@google.com (Fritz Koenig)
package com.google.libvpx;

/**
 * Holds a rational number
 *
 */
public class Rational {
  private final long num;
  private final long den;

  public Rational() {
    num = 0;
    den = 1;
  }

  public Rational(long num, long den) {
    this.num = num;
    this.den = den;
  }

  public Rational(String num, String den) {
    this.num = Integer.parseInt(num);
    this.den = Integer.parseInt(den);
  }

  public Rational multiply(Rational b) {
    return new Rational(num * b.num(), den * b.den());
  }

  public Rational multiply(int b) {
    return new Rational(num * b, den);
  }

  public Rational reciprocal() {
    return new Rational(den, num);
  }

  public float toFloat() {
    return (float) num / (float) den;
  }

  public long toLong() {
    // TODO(frkoenig) : consider adding rounding to the divide.
    return num / den;
  }

  public long num() {
    return num;
  }

  public long den() {
    return den;
  }

  @Override
  public String toString() {
    if (den == 1) {
      return new String(num + "");
    } else {
      return new String(num + "/" + den);
    }
  }

  public String toColonSeparatedString() {
    return new String(num + ":" + den);
  }
}
