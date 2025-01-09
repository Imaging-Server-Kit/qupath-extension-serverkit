# 🪐 QuPath Extension Server Kit

Connect to an [Imaging Server Kit](https://github.com/Imaging-Server-Kit/imaging-server-kit) server and run algorithms in [QuPath](https://qupath.github.io/).

## Installation

Drag and drop the latest extension jar file from the [Releases](https://github.com/Imaging-Server-Kit/qupath-extension-serverkit/releases) page into the main QuPath window, then restart QuPath. The extension is compatible with QuPath version **0.5.0** and above.

## Usage

1. Make sure you have an [algorithm server](https://github.com/Imaging-Server-Kit/imaging-server-kit) up and running that you can connect to.

2. Connect to the server via **Extensions > Imaging Server Kit > Connect...**. Enter the server URL (by default, http://localhost:8000) and click "Connect".
3. This should populate the sub-menu **Extensions > Imaging Server Kit** with the available algorithms.
4. Open an image and create an annotation on it. Use `Ctrl+Shift+A` to create an annotation on the whole image.
5. Select one of the available algorithm from the extension sub-menu. A window to set parameters for the selected algorithm will be displayed. Click "Run" to run the algorithm on the selected annotation.

## For developers: build the project

This is a Gradle project. Build it using the Gradle command: `./gradlew clean build` (for Linux and MacOS) or `./gradlew clean build` for Windows.
The build is then found in: **build/libs/qupath-extension-serverkit-[version].jar**