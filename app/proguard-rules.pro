# Release-only rules. Everything here exists because R8 removed or renamed something that is read
# by name at runtime, which a debug build never notices.

# The download engine is the one reflective corner of the app, and R8 cannot see through it, so
# its three libraries are left alone entirely instead of patching each hole as it crashes. Two
# were found the hard way in the 0.1.0 release, and both were invisible until the phone ran it:
#
#  - R8 had deleted every field of youtubedl-android's VideoInfo (title, duration, ext,
#    extractor...), the class Jackson fills from yt-dlp's JSON output. Its field names, and the
#    JSON keys in their @JsonProperty, are the protocol. getInfo() came back empty and every
#    download refused to start, because it asks what the post is before fetching it.
#  - R8 had deleted the no-arg constructors of every ZipExtraField in commons-compress, which
#    ExtraFieldUtils instantiates by reflection in its static initialiser. That left the class
#    permanently broken, so unpacking the Python runtime failed with NoClassDefFoundError and
#    yt-dlp never even started.
#
# Together they cost about two megabytes of dex on an APK whose fourteen megabytes of Python
# dwarf them, which is a cheap price for the part that has to work.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.compress.** { *; }

# The fields that hold a list or a map (formats, httpHeaders) only say what they hold in their
# generic signature, and Jackson reads it from there.
-keepattributes Signature,InnerClasses,EnclosingMethod

# Jackson 2.11 and commons-compress compile against things that do not exist on Android and are
# never reached from here.
-dontwarn java.beans.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn org.apache.commons.compress.**
