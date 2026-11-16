# Battery Info

A Java ME battery dashboard for Nokia 6303 classic / RM-443 firmware 10.12.
Build from the repository root:

```sh
bash homebrew/build.sh battery
```

Install `homebrew/dist/battery/BatteryInfo.jar`, with its matching JAD if needed.
On stock 10.12 it installs under Games; use the phone's Move command to put it in
Collection. The manifest's `Nokia-MIDlet-Category: Application` does not control
folder placement for this unsigned foreground app.

The app reads once on launch. Refresh or center/5 takes another snapshot;
Exit closes it. It shows estimated percentage, remaining and nominal capacity
in mAh, cached voltage in mV, charging state and the phone's four-bar indicator.
Percentage is remaining / nominal capacity, rounded to the nearest percent.
Nominal capacity does not measure the battery's present health.

Charging estimates combine the firmware's battery model with accumulated net
charge. If the model is unavailable, percentage and remaining capacity are
unavailable; voltage, charger state, nominal capacity and bars remain usable.
The app does not substitute a voltage-based or per-bar percentage.

## Native adapter

The shared resolver and bootstrap load `native/battery.c`. Its exact-firmware
adapter checks getter instructions and pointer literals before calling them;
the Java launcher rejects other firmware versions. It only reads battery state
and does not send charge-control requests.

The discharge conversion is `min(nominal, 2 * (estimate - 1) / 60 + 5)` mAh.
While charging pauses the discharge model, the adapter uses a read-only snapshot:
`(empty - used) / 3600000 + (charge - accounted) / 3600 + 5`, capped at nominal
capacity. The counter delta is included only while the model is paused.
Invalid or uninitialised models return unavailable; a charge-complete flag
reports full capacity. The native source defines the getter addresses, model
fields and charger-state mapping. BAT2 results use descriptor words 16–23.

Original app code is MIT licensed under `homebrew/COPYING`.
