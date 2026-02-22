# Stonks Everyday

Android application targeting Android 16 (API 36).

## 專案資訊

- **Target SDK**: Android 16 (API 36)
- **Min SDK**: Android 7.0 (API 24)
- **語言**: Kotlin
- **建構工具**: Gradle 8.9
- **Android Gradle Plugin**: 8.7.3

## 建構專案

使用 Gradle Wrapper 建構專案:

```bash
./gradlew build
```

## 安裝到裝置

```bash
./gradlew installDebug
```

## 專案結構

```
stonks_everyday/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/stonkseveryday/
│   │       │   └── MainActivity.kt
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   ├── values/
│   │       │   └── mipmap-*/
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── gradle/
├── build.gradle
└── settings.gradle
```

## 備註

啟動器圖標文件 (ic_launcher*.png) 是佔位符，請使用 Android Studio 的 Image Asset Studio 或其他工具生成正確的圖標。
