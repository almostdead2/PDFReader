[app]

# (str) Title of your application
title = PDFReader

# (str) Package name
package.name = com.pdfreader

# (str) Package domain (needed for android/ios packaging)
package.domain = 

# (str) Application versioning
version = 0.1

# (list) Source files to include (main.py is automatically included)
source.dir = .
source.include_exts = py,png,jpg,kv,json

# List of Python modules to install with pip.
# pyjnius is essential for Android Intent handling.
# pymupdf is essential for PDF rendering.
requirements = python3,kivy,pyjnius,pymupdf

# (list) Android permissions (ONLY those absolutely necessary, for 'no permissions' strategy)
# Explicitly EMPTY to ensure NO permissions are requested beyond the bare minimum for app execution.
permissions = 

# (int) Android API target level (e.g. 29, 30, 31, 32, 33)
# Starting with API 30, Android enforces scoped storage more strictly.
# Targeting 29 or higher implies handling content URIs properly.
android.api = 30 
android.minapi = 21
android.targetapi = 30

# (str) Android NDK version to use
# Make sure this is compatible with your android.api
android.ndk = 21b

# (bool) If True, then automatically accept SDK license agreements.
# This is intended for automation only.
android.accept_sdk_license = True 

# (list) Java classes to add to the Java classpath
android.add_src =

# (list) Java libraries to add to the classpath (jar, aars)
android.add_libs =

# (bool) If True, enable multidex support. Needed for larger apps or those with many dependencies.
android.enable_multidex = 1 

# (bool) If True, the build process will include debug symbols. Keep 1 for development/debugging.
android.allow_debug = 1 

# (bool) If False, the build process will not try to sign the APK. 0 for debug build.
android.release = 0 

# (str) The directory where buildozer should store its internal files
buildozer.dir = .buildozer

# (str) The directory where buildozer should store its output files
bin.dir = bin

# (list) Custom Java source files to add to the build
# This is crucial for handling ACTION_VIEW intents correctly in Kivy's PythonActivity
# This tells Android that your app can open PDF files
android.activity_class_name = org.kivy.android.PythonActivity
android.extra_xml = \
    <activity-alias android:name="com.pdfreader.PDFReaderApp" android:exported="true">\
        <intent-filter>\
            <action android:name="android.intent.action.VIEW"/>\
            <category android:name="android.intent.category.DEFAULT"/>\
            <data android:mimeType="application/pdf"/>\
        </intent-filter>\
    </activity-alias>
