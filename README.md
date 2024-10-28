## Diagram
![Solution Diagram](https://github.com/guitdeveloper/case-pin/blob/main/Case_Pin.jpg)

## How run the application

Steps:
- On Terminal execute adb shell in an rooted device. Checks if device is with access root (su).
- Use the below command to add app as device owner application.
  dpm set-device-owner br.com.gtb.simplemdm/.SimpleDeviceAdminReceiver
- you can remove the device admin capabilities by executing below command:
  dpm remove-active-admin br.com.gtb.simplemdm/.SimpleDeviceAdminReceiver 
  dpm uninstall br.com.gtb.simplemdm

