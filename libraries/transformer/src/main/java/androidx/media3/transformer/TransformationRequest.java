/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.extractor.mp4.Mp4Extractor;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A media transformation request. */
@UnstableApi
public final class TransformationRequest {

  /**
   * The strategy to use to transcode or edit High Dynamic Range (HDR) input video.
   *
   * <p>One of {@link #HDR_MODE_KEEP_HDR}, {@link #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC},
   * {@link #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL}, or {@link
   * #HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR}.
   *
   * <p>Standard Dynamic Range (SDR) input video is unaffected by these settings.
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    HDR_MODE_KEEP_HDR,
    HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC,
    HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
    HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR,
  })
  public @interface HdrMode {}
  /**
   * Processes HDR input as HDR, to generate HDR output.
   *
   * <p>Supported on API 31+, by some device and HDR format combinations.
   *
   * <p>If not supported, {@link Transformer} may fall back to {@link
   * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC}.
   */
  public static final int HDR_MODE_KEEP_HDR = 0;
  /**
   * Tone map HDR input to SDR before processing, to generate SDR output, using the {@link
   * android.media.MediaCodec} decoder tone-mapper.
   *
   * <p>Supported on API 31+, by some device and HDR format combinations. Tone-mapping is only
   * guaranteed to be supported from Android T onwards.
   *
   * <p>If not supported, {@link Transformer} throws a {@link TransformationException}.
   */
  public static final int HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC = 1;
  /**
   * Tone map HDR input to SDR before processing, to generate SDR output, using an OpenGL
   * tone-mapper.
   *
   * <p>Supported on API 29+.
   *
   * <p>This may exhibit mild differences from {@link
   * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC}, depending on the device's tone-mapping
   * implementation, but should have much wider support and have more consistent results across
   * devices.
   *
   * <p>If not supported, {@link Transformer} throws a {@link TransformationException}.
   */
  public static final int HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL = 2;
  /**
   * Interpret HDR input as SDR, likely with a washed out look.
   *
   * <p>Supported on API 29+.
   *
   * <p>This is much more widely supported than {@link #HDR_MODE_KEEP_HDR} and {@link
   * #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC}. However, as HDR transfer functions and
   * metadata will be ignored, contents will be displayed incorrectly, likely with a washed out
   * look.
   *
   * <p>Use of this flag may result in {@code
   * TransformationException.ERROR_CODE_HDR_DECODING_UNSUPPORTED} or {@code
   * ERROR_CODE_DECODING_FORMAT_UNSUPPORTED}.
   *
   * <p>This field is experimental, and will be renamed or removed in a future release.
   */
  public static final int HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR = 3;

  /** A builder for {@link TransformationRequest} instances. */
  public static final class Builder {

    private boolean flattenForSlowMotion;
    private int outputHeight;
    @Nullable private String audioMimeType;
    @Nullable private String videoMimeType;
    private @HdrMode int hdrMode;

    /**
     * Creates a new instance with default values.
     *
     * <p>Use {@link TransformationRequest#buildUpon()} to obtain a builder representing an existing
     * {@link TransformationRequest}.
     */
    public Builder() {
      outputHeight = C.LENGTH_UNSET;
    }

    private Builder(TransformationRequest transformationRequest) {
      this.flattenForSlowMotion = transformationRequest.flattenForSlowMotion;
      this.outputHeight = transformationRequest.outputHeight;
      this.audioMimeType = transformationRequest.audioMimeType;
      this.videoMimeType = transformationRequest.videoMimeType;
      this.hdrMode = transformationRequest.hdrMode;
    }

    /**
     * Sets whether the input should be flattened for media containing slow motion markers.
     *
     * <p>The transformed output is obtained by removing the slow motion metadata and by actually
     * slowing down the parts of the video and audio streams defined in this metadata. The default
     * value for {@code flattenForSlowMotion} is {@code false}.
     *
     * <p>Only Samsung Extension Format (SEF) slow motion metadata type is supported. The
     * transformation has no effect if the input does not contain this metadata type.
     *
     * <p>For SEF slow motion media, the following assumptions are made on the input:
     *
     * <ul>
     *   <li>The input container format is (unfragmented) MP4.
     *   <li>The input contains an AVC video elementary stream with temporal SVC.
     *   <li>The recording frame rate of the video is 120 or 240 fps.
     * </ul>
     *
     * <p>If using an {@link ExoPlayerAssetLoader.Factory} with a provided {@link
     * MediaSource.Factory}, make sure that {@link Mp4Extractor#FLAG_READ_SEF_DATA} is set on the
     * {@link Mp4Extractor} used. Otherwise, the slow motion metadata will be ignored and the input
     * won't be flattened.
     *
     * <p>Using slow motion flattening together with {@link
     * androidx.media3.common.MediaItem.ClippingConfiguration} is not supported yet.
     *
     * @param flattenForSlowMotion Whether to flatten for slow motion.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setFlattenForSlowMotion(boolean flattenForSlowMotion) {
      this.flattenForSlowMotion = flattenForSlowMotion;
      return this;
    }

    /**
     * Sets the output resolution using the output height of the displayed video.
     *
     * <p>Output width of the displayed video will scale to preserve the video's aspect ratio after
     * other transformations.
     *
     * <p>For example, a 1920x1440 video can be scaled to 640x480 by calling setResolution(480).
     *
     * <p>The default value, {@link C#LENGTH_UNSET}, leaves the width and height unchanged.
     *
     * <p>Note that the output encoded video's dimensions may be swapped from the displayed video's
     * dimensions, if the displayed video's height > width. This is to improve compatibility among
     * different device encoders.
     *
     * @param outputHeight The output height of the displayed video, in pixels.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setResolution(int outputHeight) {
      this.outputHeight = outputHeight;
      return this;
    }

    /**
     * Sets the video MIME type of the output.
     *
     * <p>The default value is {@code null} which corresponds to using the same MIME type as the
     * input. Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#VIDEO_H263}
     *   <li>{@link MimeTypes#VIDEO_H264}
     *   <li>{@link MimeTypes#VIDEO_H265} from API level 24
     *   <li>{@link MimeTypes#VIDEO_MP4V}
     * </ul>
     *
     * @param videoMimeType The MIME type of the video samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the {@code videoMimeType} is non-null but not a video
     *     {@linkplain MimeTypes MIME type}.
     */
    @CanIgnoreReturnValue
    public Builder setVideoMimeType(@Nullable String videoMimeType) {
      checkArgument(
          videoMimeType == null || MimeTypes.isVideo(videoMimeType),
          "Not a video MIME type: " + videoMimeType);
      this.videoMimeType = videoMimeType;
      return this;
    }

    /**
     * Sets the audio MIME type of the output.
     *
     * <p>The default value is {@code null} which corresponds to using the same MIME type as the
     * input. Supported MIME types are:
     *
     * <ul>
     *   <li>{@link MimeTypes#AUDIO_AAC}
     *   <li>{@link MimeTypes#AUDIO_AMR_NB}
     *   <li>{@link MimeTypes#AUDIO_AMR_WB}
     * </ul>
     *
     * @param audioMimeType The MIME type of the audio samples in the output.
     * @return This builder.
     * @throws IllegalArgumentException If the {@code audioMimeType} is non-null but not an audio
     *     {@linkplain MimeTypes MIME type}.
     */
    @CanIgnoreReturnValue
    public Builder setAudioMimeType(@Nullable String audioMimeType) {
      checkArgument(
          audioMimeType == null || MimeTypes.isAudio(audioMimeType),
          "Not an audio MIME type: " + audioMimeType);
      this.audioMimeType = audioMimeType;
      return this;
    }

    /**
     * Sets the {@link HdrMode} for HDR video input.
     *
     * <p>The default value is {@link #HDR_MODE_KEEP_HDR}.
     *
     * @param hdrMode The {@link HdrMode} used.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setHdrMode(@HdrMode int hdrMode) {
      this.hdrMode = hdrMode;
      return this;
    }

    /**
     * @deprecated This method is now a no-op if {@code false}, and sets {@code
     *     setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC)} if {@code true}. Use {@link
     *     #setHdrMode} with {@link #HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder setEnableRequestSdrToneMapping(boolean enableRequestSdrToneMapping) {
      if (enableRequestSdrToneMapping) {
        return setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC);
      }
      return this;
    }

    /**
     * @deprecated This method is now a no-op if {@code false}, and sets {@code
     *     setHdrMode(HDR_MODE_KEEP_HDR)} if {@code true}. {@code
     *     experimental_setEnableHdrEditing(true)} is now the default behavior. Use {@link
     *     #setHdrMode} with link {@link #HDR_MODE_KEEP_HDR} instead.
     */
    @Deprecated
    @CanIgnoreReturnValue
    public Builder experimental_setEnableHdrEditing(boolean enableHdrEditing) {
      if (enableHdrEditing) {
        return setHdrMode(HDR_MODE_KEEP_HDR);
      }
      return this;
    }

    /** Builds a {@link TransformationRequest} instance. */
    public TransformationRequest build() {
      return new TransformationRequest(
          flattenForSlowMotion, outputHeight, audioMimeType, videoMimeType, hdrMode);
    }
  }

  /**
   * Whether the input should be flattened for media containing slow motion markers.
   *
   * @see Builder#setFlattenForSlowMotion(boolean)
   */
  public final boolean flattenForSlowMotion;
  /**
   * The requested height of the output video, or {@link C#LENGTH_UNSET} if inferred from the input.
   *
   * @see Builder#setResolution(int)
   */
  public final int outputHeight;
  /**
   * The requested output audio sample {@linkplain MimeTypes MIME type}, or {@code null} if inferred
   * from the input.
   *
   * @see Builder#setAudioMimeType(String)
   */
  @Nullable public final String audioMimeType;
  /**
   * The requested output video sample {@linkplain MimeTypes MIME type}, or {@code null} if inferred
   * from the input.
   *
   * @see Builder#setVideoMimeType(String)
   */
  @Nullable public final String videoMimeType;
  /**
   * The {@link HdrMode} specifying how to handle HDR input video.
   *
   * @see Builder#setHdrMode(int)
   */
  public final @HdrMode int hdrMode;

  private TransformationRequest(
      boolean flattenForSlowMotion,
      int outputHeight,
      @Nullable String audioMimeType,
      @Nullable String videoMimeType,
      @HdrMode int hdrMode) {

    this.flattenForSlowMotion = flattenForSlowMotion;
    this.outputHeight = outputHeight;
    this.audioMimeType = audioMimeType;
    this.videoMimeType = videoMimeType;
    this.hdrMode = hdrMode;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TransformationRequest)) {
      return false;
    }
    TransformationRequest that = (TransformationRequest) o;
    return flattenForSlowMotion == that.flattenForSlowMotion
        && outputHeight == that.outputHeight
        && Util.areEqual(audioMimeType, that.audioMimeType)
        && Util.areEqual(videoMimeType, that.videoMimeType)
        && hdrMode == that.hdrMode;
  }

  @Override
  public int hashCode() {
    int result = (flattenForSlowMotion ? 1 : 0);
    result = 31 * result + outputHeight;
    result = 31 * result + (audioMimeType != null ? audioMimeType.hashCode() : 0);
    result = 31 * result + (videoMimeType != null ? videoMimeType.hashCode() : 0);
    result = 31 * result + hdrMode;
    return result;
  }

  /**
   * Returns a new {@link TransformationRequest.Builder} initialized with the values of this
   * instance.
   */
  public Builder buildUpon() {
    return new Builder(this);
  }
}
