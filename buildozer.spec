[app]

title = PDFReader
package.name = com.yourcompany.pdfreader
package.domain = org.example
version = 0.1
source.dir = .
source.include_exts = py,png,jpg,kv,json

# List of Python modules to install with pip.
# pyjnius is essential for Android Intent handling.
# pymupdf is essential for PDF rendering.
requirements = python3,kivy,pyjnius,pymupdf

# (list) Android permissions (ONLY those absolutely necessary, for 'no permissions' strategy)
# NO READ_EXTERNAL_STORAGE or WRITE_EXTERNAL_STORAGE!
# INTERNET is often added by default by Kivy/Buildozer, but for a truly offline app, it could be removed
# if no internet-dependent modules are used. It's safe if not used.
permissions = INTERNET

# (int) Android API target level (e.g. 29, 30, 31, 32, 33)
# Starting with API 30, Android enforces scoped storage more strictly.
# Targeting 29 or higher implies handling content URIs properly.
android.api = 30 
android.minapi = 21
android.targetapi = 30

# (str) Android NDK version to use
# Make sure this is compatible with your android.api
android.ndk = 21b

# (list) Java classes to add to the Java classpath
android.add_src =

# (list) Java libraries to add to the classpath (jar, aars)
android.add_libs =

# (list) Extra Java packages to add to the build (e.g. "org.apache.commons:commons-io:1.3.2")
android.enable_multidex = 1 # Often needed for larger apps or those with many dependencies
android.allow_debug = 1 # Keep 1 for development/debugging, change to 0 for release
android.release = 0 # 0 for debug build, 1 for release (needs signing)

# (list) Custom Java source files to add to the build
# This is crucial for handling ACTION_VIEW intents correctly in Kivy's PythonActivity
# This tells Android that your app can open PDF files
android.activity_class_name = org.kivy.android.PythonActivity
android.extra_xml = \
    <activity-alias android:name="org.example.pdfreader.PDFReaderApp" android:exported="true">\
        <intent-filter>\
            <action android:name="android.intent.action.VIEW"/>\
            <category android:name="android.intent.category.DEFAULT"/>\
            <data android:mimeType="application/pdf"/>\
        </intent-filter>\
    </activity-alias>
