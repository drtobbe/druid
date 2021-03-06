/*
 * Druid - a distributed column store.
 * Copyright (C) 2012  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.metamx.druid.aggregation;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.codehaus.jackson.annotate.JsonValue;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Histogram
{
  public float[] breaks;
  public long[]  bins;
  public transient long count;
  public float min;
  public float max;

  public Histogram(float[] breaks) {
    Preconditions.checkArgument(breaks != null, "Histogram breaks must not be null");

    this.breaks = breaks;
    this.bins   = new long[this.breaks.length + 1];
    this.count  = 0;
    this.min = Float.MAX_VALUE;
    this.max = Float.MIN_VALUE;
  }

  public Histogram(float[] breaks, long[] bins, float min, float max) {
    this.breaks = breaks;
    this.bins   = bins;
    this.min = min;
    this.max = max;
    for(long k : bins) this.count += k;
  }

  public void offer(float d) {
    if(d > max) max = d;
    if(d < min) min = d;

    int index = Arrays.binarySearch(breaks, d);
    int pos = (index >= 0) ? index : -(index + 1);
    bins[pos]++;
    count++;
  }

  public Histogram fold(Histogram h) {
    Preconditions.checkArgument(Arrays.equals(breaks, h.breaks), "Cannot fold histograms with different breaks");

    if(h.min < min) min = h.min;
    if(h.max > max) max = h.max;

    count += h.count;
    for (int i = 0; i < bins.length; ++i) {
      bins[i] += h.bins[i];
    }
    return this;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Histogram histogram = (Histogram) o;

    if (count != histogram.count) {
      return false;
    }
    if (Float.compare(histogram.max, max) != 0) {
      return false;
    }
    if (Float.compare(histogram.min, min) != 0) {
      return false;
    }
    if (!Arrays.equals(bins, histogram.bins)) {
      return false;
    }
    if (!Arrays.equals(breaks, histogram.breaks)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode()
  {
    int result = Arrays.hashCode(breaks);
    result = 31 * result + Arrays.hashCode(bins);
    result = 31 * result + (min != +0.0f ? Float.floatToIntBits(min) : 0);
    result = 31 * result + (max != +0.0f ? Float.floatToIntBits(max) : 0);
    return result;
  }

  @JsonValue
  public byte[] toBytes() {
    ByteBuffer buf = ByteBuffer.allocate(Ints.BYTES + Floats.BYTES * breaks.length +
                                         Longs.BYTES * bins.length + Floats.BYTES * 2);

    buf.putInt(breaks.length);
    for(float b : breaks) buf.putFloat(b);
    for(long  c : bins  ) buf.putLong(c);
    buf.putFloat(min);
    buf.putFloat(max);

    return buf.array();
  }

  /**
   * Returns a visual representation of a histogram object.
   * Initially returns an array of just the min. and max. values
   * but can also support the addition of quantiles.
   */
  public HistogramVisual asVisual() {
    float[] visualCounts = new float[bins.length - 2];
    for(int i = 0; i < visualCounts.length; ++i) visualCounts[i] = (float)bins[i + 1];
    return new HistogramVisual(breaks, visualCounts, new float[]{min, max});
  }

  public static Histogram fromBytes(byte[] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    return fromBytes(buf);
  }

  public static Histogram fromBytes(ByteBuffer buf) {
    int n = buf.getInt();
    float[] breaks = new float[n];
    long[]  bins   = new long[n + 1];

    for (int i = 0; i < breaks.length; ++i) breaks[i] = buf.getFloat();
    for (int i = 0; i < bins.length  ; ++i) bins[i]   = buf.getLong();

    float min = buf.getFloat();
    float max = buf.getFloat();

    return new Histogram(breaks, bins, min, max);
  }

  @Override
  public String toString()
  {
    return "Histogram{" +
           "bins=" + Arrays.toString(bins) +
           ", count=" + count +
           ", breaks=" + Arrays.toString(breaks) +
           '}';
  }
}
