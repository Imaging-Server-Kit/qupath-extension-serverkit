# Imaging Server Kit: QuPath Extension

## Installation

Drag and drop the latest extension jar file from the [Releases](https://github.com/Imaging-Server-Kit/qupath-extension-serverkit/releases) page into the main QuPath window, then restart QuPath. The extension is compatible with QuPath version **0.5.0** and above.

## Usage

1. Setup and launch the [server](https://github.com/Imaging-Server-Kit/imaging-server-kit).

The Serverkit extension is available under the **Extensions** menu.

1. Connect to the server via **Extensions > Imaging Server Kit > Connect...**. Enter the server URL. Once successfully connected to the server, the available algorithms are populated
   in the sub-menus of **Python algos**.

<!-- ![screenshot_connect](readme_images/connection_window.png "Connection window") -->

1. Create an annotation on an image. For single-channel or RGB images, all the pixels selected will be sent. For multi-channel fluorescence images, only the channel currently selected is sent, and if multiple channels are selected, then an RGB-rendering of the selected channels is sent.

2. Select one of the available algorithm from the **PyAlgos** menu. If there are parameters required for the selected algorithm, as defined on the Python API, a window to set those parameters will be displayed. If the algorithm has no tunable parameters, it will be run directly.

<!-- ![screenshot_parameters](readme_images/parameters_window.png "Parameters window") -->

4. Once the processing is completed successfully, the resulting objects (typically detected cells) are displayed in
   QuPath.
   The objects contain their associated measurements (e.g. the detection probability) and classification.
   On the Python API, the objects correspond to geojson features, defined by their geometry
   (e.g. the coordinates of the contour of the object) and properties.

## For developers: build the project

This is a Gradle project. Build it using the Gradle command: `./gradlew clean build` (for Linux and MacOS) or `./gradlew clean build` for Windows.
The build is then found in: **build/libs/qupath-extension-serverkit-[version].jar**