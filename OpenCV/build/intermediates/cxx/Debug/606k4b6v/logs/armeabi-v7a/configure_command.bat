@echo off
"D:\\DevelopeTools\\Android\\SDK\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HD:\\AndroidStudioProjects\\CameraXScan\\OpenCV\\libcxx_helper" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=21" ^
  "-DANDROID_PLATFORM=android-21" ^
  "-DANDROID_ABI=armeabi-v7a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=armeabi-v7a" ^
  "-DANDROID_NDK=D:\\DevelopeTools\\Android\\SDK\\ndk\\26.1.10909125" ^
  "-DCMAKE_ANDROID_NDK=D:\\DevelopeTools\\Android\\SDK\\ndk\\26.1.10909125" ^
  "-DCMAKE_TOOLCHAIN_FILE=D:\\DevelopeTools\\Android\\SDK\\ndk\\26.1.10909125\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=D:\\DevelopeTools\\Android\\SDK\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_CXX_FLAGS=-std=c++14 -frtti -fexceptions" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=D:\\AndroidStudioProjects\\CameraXScan\\OpenCV\\build\\intermediates\\cxx\\Debug\\606k4b6v\\obj\\armeabi-v7a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=D:\\AndroidStudioProjects\\CameraXScan\\OpenCV\\build\\intermediates\\cxx\\Debug\\606k4b6v\\obj\\armeabi-v7a" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BD:\\AndroidStudioProjects\\CameraXScan\\OpenCV\\.cxx\\Debug\\606k4b6v\\armeabi-v7a" ^
  -GNinja ^
  "-DANDROID_STL=c++_shared"
