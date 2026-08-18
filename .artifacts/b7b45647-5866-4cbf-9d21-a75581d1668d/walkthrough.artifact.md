# Walkthrough - Fixed Unresolved Reference 'setDeviceLayoutMode'

I have fixed the build error by implementing the missing `setDeviceLayoutMode` method in `IPTVViewModel` and integrating the `DeviceSelectionScreen` into the app's navigation flow.

## Changes Made

### [IPTVViewModel](file:///C:/Users/iago/Downloads/UnlockT3am_Player_1.0_hotfix-fonte/app/src/main/java/com/example/ui/IPTVViewModel.kt)
- Added `DEVICE_SELECTION` to the `Screen` enum.
- Implemented `setDeviceLayoutMode(mode: String)`, which saves the selection to storage and navigates to the next appropriate screen (Home or Login).
- Updated the `init` block to check for a stored `deviceLayoutMode`. If it's "UNSET", the app now correctly starts on the `DEVICE_SELECTION` screen instead of defaulting to "MOBILE".

### [MainActivity](file:///C:/Users/iago/Downloads/UnlockT3am_Player_1.0_hotfix-fonte/app/src/main/java/com/example/MainActivity.kt)
- Modified `showNavigation` logic to hide the bottom navigation bar when on the `DEVICE_SELECTION` screen.
- Added `Screen.DEVICE_SELECTION` to `MainContentRouting` so the `DeviceSelectionScreen` can be rendered.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` which finished successfully, confirming that the "Unresolved reference" error is resolved.

### Manual Verification Required
- Launch the app. If it's a first-time launch, you should be greeted with the "Selecione o seu Dispositivo" screen.
- Verify that selecting "Modo TV" or "Modo Celular" works and takes you to the Login/Home screen.
