# Release-only rules. Everything here exists because R8 removed or renamed something that is read
# by name at runtime, which a debug build never notices.

# yt-dlp answers in JSON and youtubedl-android hands that JSON to Jackson, which fills these
# classes by reflection: their field names are the protocol. Measured on the 0.1.0 release build,
# in app/build/outputs/mapping/release/usage.txt: R8 had deleted every field of VideoInfo
# (title, duration, ext, extractor...). getInfo() then answered with nothing, and since a download
# asks what the post is before fetching it, every download refused to start.
-keep class com.yausername.youtubedl_android.mapper.** { *; }

# The JSON key of each of those fields lives in its @JsonProperty, so the annotation's own methods
# have to survive too; R8 had stripped the members of JsonProperty and JsonIgnoreProperties.
-keep class com.fasterxml.jackson.annotation.** { *; }

# The fields that hold a list or a map (formats, httpHeaders) only say what they hold in their
# generic signature, and Jackson reads it from there.
-keepattributes Signature,InnerClasses,EnclosingMethod

# Jackson 2.11 looks this one up with Class.forName and swallows the failure, so losing it would
# not crash: it would quietly change how some values are read.
-keep class com.fasterxml.jackson.databind.ext.Java7SupportImpl { *; }
-dontwarn com.fasterxml.jackson.databind.ext.**

# Jackson 2.11 compiles against a few things that do not exist on Android and are never reached.
-dontwarn java.beans.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.bootstrap.**
