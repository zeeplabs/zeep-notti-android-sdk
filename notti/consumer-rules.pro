# R8/ProGuard rules shipped to host apps inside the AAR (as proguard.txt).
# No keep rules needed so far: AGP keeps the manifest-declared components (provider, FCM service, permission
# Activity) through its aapt rules, and hosts reference NottiBridge directly. CI proves it with the minified
# :example release build (push-integration T8).
