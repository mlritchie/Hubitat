<p><span style="text-decoration: underline; font-size: 12pt;"><strong>NUT UPS Monitor</strong></span></p>
<p>NAS Settings</p>
<ul style="list-style-position: inside;">
<li>I personally have a QNAP NAS and I navigate into the UPS settings to allow the HE hub to connect</li>
<li>In Control Panel, External Device, UPS tab, make sure that Enable Network UPS master is checked and enter the IP address of your HE hub into the list and click apply</li>
<li>HE doesn't require a user or password and adding the IP address here allowed a connection.</li>
</ul>
<p>&nbsp;</p>
<p>Installation:</p>
<ul style="list-style-position: inside;">
<li>Download and install both the Device and App code in your HE hub</li>
<li>Navigate to Apps and click Add User App and install the NUT UPS Monitor
<ul>
<li>This app automatically creates the child device</li>
</ul>
</li>
<li>Click into the UPS Network Settings and name your child UPS device, set the NUT server and port number and polling interval</li>
<li>If interested click into Notification Preferences and set the notifications you wish to receive</li>
<li>If interested click into Action Settings if you wish to shut down your HE hub based on a battery percent or perform other REST Post or Get action to do such things as shut down additional HE hubs</li>
<li>Debugging is disabled by default however feel free to enable it.&nbsp; Turning it on in the app will also debug data in the child device.&nbsp; This does generate a ton of logging so make sure you turn it off when you are done debugging.</li>
</ul>
