# Local wallpaper test fixture

`wallpaper-test.mp4` is an original FFmpeg `testsrc2` pattern, without audio or downloaded artwork. H.264 constrained baseline, 320 × 180, 15 fps, eight seconds. Generated locally with:

```sh
ffmpeg -f lavfi -i testsrc2=size=320x180:rate=15 -t 8 -c:v libopenh264 -profile:v constrained_baseline -b:v 120k -pix_fmt yuv420p -an -movflags +faststart wallpaper-test.mp4
```

Used only by instrumentation tests; it is not bundled in the launcher APK.
