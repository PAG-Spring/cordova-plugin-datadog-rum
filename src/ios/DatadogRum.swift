import Foundation

@objc(DatadogRum)

class DatadogRum : CDVPlugin {

    @objc(init:)
    func initialize(
        command: CDVInvokedUrlCommand) {

        let result =
            CDVPluginResult(
                status: CDVCommandStatus_OK)

        self.commandDelegate.send(
            result,
            callbackId: command.callbackId)
    }
}