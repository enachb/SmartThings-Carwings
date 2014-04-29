preferences {
  input("username", "text", title: "Username", description: "Your Carwings username")
  input("password", "password", title: "Password", description: "Your Carwings password")
}

metadata {
  definition (name: "carwings", author: "mick@staugaard.com") {
    capability "Battery"
    capability "Polling"
    attribute "charging", "string"
    command "requestUpdate"
  }

  simulator {
    // TODO: define status and reply messages here
  }

  tiles {
    valueTile('battery', 'device.battery', decoration: 'flat', inactiveLabel: false) {
      state 'battery', label: '${currentValue}%'//, unit: "%"
    }
    standardTile("refresh", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
        state "default", action: "polling.poll", icon: "st.secondary.refresh"
    }
    main "battery"
    details(["battery", "refresh"])
  }
}

// parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
  // TODO: handle 'battery' attribute

}

def login() {
  log.debug "Executing 'login'"
  def params = [
    uri: 'https://nissan-na-smartphone-biz.viaaq.com/aqPortal/smartphoneProxy/userService',
    headers: [
      'User-Agent': 'NissanLEAF/1.40 CFNetwork/485.13.9 Darwin/11.0.0 SmartThings',
      'Content-Type': 'text/xml'
    ],
    body: '<?xml version="1.0"?>' +
'<ns2:SmartphoneLoginWithAdditionalOperationRequest' +
'  xmlns:ns4="urn:com:hitachi:gdc:type:report:v1" xmlns:ns7="urn:com:airbiquity:smartphone.vehicleservice:v1"' +
'  xmlns:ns3="http://www.nissanusa.com/owners/schemas/api/0" xmlns:ns5="urn:com:airbiquity:smartphone.reportservice:v1"' +
'  xmlns:ns2="urn:com:airbiquity:smartphone.userservices:v1" xmlns:ns6="urn:com:hitachi:gdc:type:vehicle:v1">' +
'  <SmartphoneLoginInfo>' +
'    <UserLoginInfo>' +
'      <userId>' + settings.username + '</userId>' +
'      <userPassword>' + settings.password + '</userPassword>' +
'    </UserLoginInfo>' +
'    <DeviceToken>DUMMY1313820504.22296500</DeviceToken>' +
'    <UUID>SmartThings:' + settings.username + '</UUID>' +
'    <Locale>US</Locale>' +
'    <AppVersion>1.40</AppVersion>' +
'    <SmartphoneType>IPHONE</SmartphoneType>' +
'  </SmartphoneLoginInfo>' +
'  <SmartphoneOperationType>SmartphoneLatestBatteryStatusRequest</SmartphoneOperationType>' +
'</ns2:SmartphoneLoginWithAdditionalOperationRequest>'
  ]

  def success = { response ->
    log.debug "Request was successful"

    data.cookies = []
    response.headerIterator('Set-Cookie').each {
      data.cookies.add(it.value.split(';')[0])
    }

    data.vin = response.data.SmartphoneLatestBatteryStatusResponse.SmartphoneBatteryStatusResponseType.VehicleInfo.Vin.toString()

    def batteryStatus = response.data.SmartphoneLatestBatteryStatusResponse.SmartphoneBatteryStatusResponseType.BatteryStatusRecords.BatteryStatus
    data.remaining = batteryStatus.BatteryRemainingAmount.toFloat()
    data.capacity  = batteryStatus.BatteryCapacity.toFloat()
    data.battery   = (data.remaining * 100 / data.capacity).round()
    data.charging  = batteryStatus.BatteryChargingStatus.toString()
    log.debug data
    sendEvent(name: 'battery',  value: data.battery)
    sendEvent(name: 'charging', value: data.charging)
  }

  httpPost(params, success)
}

def requestBatteryStatusCheck() {
  log.debug "Executing 'requestBatteryStatusCheck'"
  def params = [
    uri: 'https://nissan-na-smartphone-biz.viaaq.com/aqPortal/smartphoneProxy/vehicleService',
    headers: [
      'User-Agent': 'NissanLEAF/1.40 CFNetwork/485.13.9 Darwin/11.0.0 SmartThings',
      'Content-Type': 'text/xml',
      'Cookie': data.cookies.join(";")
    ],
    body: '<?xml version="1.0"?>' +
'<ns4:SmartphoneRemoteBatteryStatusCheckRequest' +
'  xmlns:ns4="urn:com:airbiquity:smartphone.vehicleservice:v1"' +
'  xmlns:ns3="urn:com:hitachi:gdc:type:vehicle:v1"' +
'  xmlns:ns2="urn:com:hitachi:gdc:type:portalcommon:v1">' +
'  <ns3:BatteryStatusCheckRequest>' +
'    <ns3:VehicleServiceRequestHeader>' +
'      <ns2:VIN>' + data.vin + '</ns2:VIN>' +
'    </ns3:VehicleServiceRequestHeader>' +
'  </ns3:BatteryStatusCheckRequest>' +
'</ns4:SmartphoneRemoteBatteryStatusCheckRequest>'
  ]

  def success = { response ->
    log.debug "Request was successful"
  }

  data.lastBatteryStatusCheck = (new Date()).time
  httpPost(params, success)
}

// handle commands
def poll() {
  log.debug "Executing 'poll'"

  login()

  def interval = 1000 * 60 * 10
  def debounced = (new Date()).time - 1000 * 60 * 10

  if (data.vin != null && (data.lastBatteryStatusCheck == null || data.lastBatteryStatusCheck < debounced)) {
    requestBatteryStatusCheck()
  }
}

def requestUpdate() {
  login()
  requestBatteryStatusCheck()
}
