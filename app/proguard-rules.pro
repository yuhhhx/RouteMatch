# Keep security-related classes
-keep class com.example.routematch.SecurityUtils { *; }
-keep class com.example.routematch.CryptoUtil { *; }

# Keep Room entities
-keep class com.example.routematch.db.** { *; }

# Keep notification listener
-keep class com.example.routematch.NotificationListener { *; }

# Keep floating service
-keep class com.example.routematch.FloatingService { *; }
