/**
 * Example of using libmuse library on android.
 * Interaxon, Inc. 2016
 */

package com.tssg.sleepanalyzer_kotlin

// kt:

import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
// kt:

import java.lang.ref.WeakReference
import java.util.ArrayList

// kt:
import java.util.Calendar
// kt:

import java.io.File

import java.util.concurrent.atomic.AtomicReference

import com.choosemuse.example.libmuse.R
import com.choosemuse.libmuse.Accelerometer
import com.choosemuse.libmuse.AnnotationData
import com.choosemuse.libmuse.ConnectionState
import com.choosemuse.libmuse.Eeg
import com.choosemuse.libmuse.LibmuseVersion
import com.choosemuse.libmuse.MessageType
import com.choosemuse.libmuse.Muse
import com.choosemuse.libmuse.MuseArtifactPacket
import com.choosemuse.libmuse.MuseConfiguration
import com.choosemuse.libmuse.MuseConnectionListener
import com.choosemuse.libmuse.MuseConnectionPacket
import com.choosemuse.libmuse.MuseDataListener
import com.choosemuse.libmuse.MuseDataPacket
import com.choosemuse.libmuse.MuseDataPacketType
import com.choosemuse.libmuse.MuseFileFactory
import com.choosemuse.libmuse.MuseFileReader
import com.choosemuse.libmuse.MuseFileWriter
import com.choosemuse.libmuse.MuseListener
import com.choosemuse.libmuse.MuseManagerAndroid
import com.choosemuse.libmuse.MuseVersion
import com.choosemuse.libmuse.Result
import com.choosemuse.libmuse.ResultLevel

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.bluetooth.BluetoothAdapter


import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

//kt:
import android.media.ToneGenerator
import android.media.AudioManager

// kt:

// kt:
/* new stuff
  HORSESHOE
  ALPHA_ABSOLUTE);
  ALPHA_SCORE);
  BETA_ABSOLUTE);
  BETA_RELATIVE);
  BETA_SCORE);
  DELTA_ABSOLUTE);
  DELTA_RELATIVE);
  DELTA_SCORE);
  GAMMA_ABSOLUTE);
  GAMMA_RELATIVE);
  GAMMA_SCORE);
  THETA_ABSOLUTE);
  THETA_RELATIVE);
  THETA_SCORE);
*/
// does not exist : import java.awt.Toolkit;
// kt:


/**
 * This example will illustrate how to connect to a Muse headband,
 * register for and receive EEG data and disconnect from the headband.
 * Saving EEG data to a .muse file is also covered.
 *
 *
 * For instructions on how to pair your headband with your Android device
 * please see:
 * http://developer.choosemuse.com/hardware-firmware/bluetooth-connectivity/developer-sdk-bluetooth-connectivity-2
 *
 *
 * Usage instructions:
 * 1. Pair your headband if necessary.
 * 2. Run this project.
 * 3. Turn on the Muse headband.
 * 4. Press "Refresh". It should display all paired Muses in the Spinner drop down at the
 * top of the screen.  It may take a few seconds for the headband to be detected.
 * 5. Select the headband you want to connect to and press "Connect".
 * 6. You should see EEG and accelerometer data as well as connection status,
 * version information and relative alpha values appear on the screen.
 * 7. You can pause/resume data transmission with the button at the bottom of the screen.
 * 8. To disconnect from the headband, press "Disconnect"
 */
class MainActivity : Activity(), OnClickListener {

	/**
	 * Tag used for logging purposes.
	 */
	protected val TAG = javaClass.simpleName

	// smf
	internal var fromFile = false


	/**
	 * The MuseManager is how you detect Muse headbands and receive notifications
	 * when the list of available headbands changes.
	 */
	private var manager: MuseManagerAndroid? = null

	/**
	 * A Muse refers to a Muse headband.  Use this to connect/disconnect from the
	 * headband, register listeners to receive EEG data and get headband
	 * configuration and version information.
	 */
	private var muse: Muse? = null

	/**
	 * The ConnectionListener will be notified whenever there is a change in
	 * the connection state of a headband, for example when the headband connects
	 * or disconnects.
	 *
	 *
	 * Note that ConnectionListener is an inner class at the bottom of this file
	 * that extends MuseConnectionListener.
	 */
	private var connectionListener: ConnectionListener? = null

	/**
	 * The DataListener is how you will receive EEG (and other) data from the
	 * headband.
	 *
	 *
	 * Note that DataListener is an inner class at the bottom of this file
	 * that extends MuseDataListener.
	 */
	private var dataListener: DataListener? = null

	/**
	 * Data comes in from the headband at a very fast rate; 220Hz, 256Hz or 500Hz,
	 * depending on the type of headband and the preset configuration.  We buffer the
	 * data that is read until we can update the UI.
	 *
	 *
	 * The stale flags indicate whether or not new data has been received and the buffers
	 * hold the values of the last data packet received.  We are displaying the EEG, ALPHA_RELATIVE
	 * and ACCELEROMETER values in this example.
	 *
	 *
	 * Note: the array lengths of the buffers are taken from the comments in
	 * MuseDataPacketType, which specify 3 values for accelerometer and 6
	 * values for EEG and EEG-derived packets.
	 */
	private val eegBuffer = DoubleArray(6)
	private var eegStale: Boolean = false
	private val alphaBuffer = DoubleArray(6)
	private var alphaStale: Boolean = false
	private val accelBuffer = DoubleArray(3)
	private var accelStale: Boolean = false

	/**
	 * We will be updating the UI using a handler instead of in packet handlers because
	 * packets come in at a very high frequency and it only makes sense to update the UI
	 * at about 60fps. The update functions do some string allocation, so this reduces our memory
	 * footprint and makes GC pauses less frequent/noticeable.
	 */
	private val handler = Handler()

	/**
	 * In the UI, the list of Muses you can connect to is displayed in a Spinner object for this example.
	 * This spinner adapter contains the MAC addresses of all of the headbands we have discovered.
	 */
	private var spinnerAdapter: ArrayAdapter<String>? = null

	/**
	 * It is possible to pause the data transmission from the headband.  This boolean tracks whether
	 * or not the data transmission is enabled as we allow the user to pause transmission in the UI.
	 */
	private var dataTransmission = true

	/**
	 * To save data to a file, you should use a MuseFileWriter.  The MuseFileWriter knows how to
	 * serialize the data packets received from the headband into a compact binary format.
	 * To read the file back, you would use a MuseFileReader.
	 */
	private val fileWriter = AtomicReference<MuseFileWriter>()

	// kt:
	private val writeData: FileWriter? = null
	private val print_line: PrintWriter? = null
	// kt:

	/**
	 * We don't want file operations to slow down the UI, so we will defer those file operations
	 * to a handler on a separate thread.
	 */
	private val fileHandler = AtomicReference<Handler>()

	// kt:
	internal var waive_pkt_cnt = 0
	internal var data_set_cnt = 0
	internal var data_time_stamp: Long = 0
	internal var data_time_stamp_ref: Long = 0
	internal var pkt_timestamp: Long = 0
	internal var pkt_timestamp_ref: Long = 0

	internal var eegElem = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
	internal var alphaAbsoluteElem = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
	internal var betaAbsoluteElem = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
	internal var deltaAbsoluteElem = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
	internal var gammaAbsoluteElem = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
	internal var thetaAbsoluteElem = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
	var horseshoeElem = intArrayOf(0, 0, 0, 0)
	// | Artifacts;

	internal var got_data_mask = 0

	// sound stuff
	var tone_on_duration = 4000
	var tone_off_duration = 3000
	var horseshoeElemeTone = 0
	var stopSounds = 0

	val isBluetoothEnabled: Boolean
		get() = BluetoothAdapter.getDefaultAdapter().isEnabled

	/**
	 * The runnable that is used to update the UI at 60Hz.
	 *
	 *
	 * We update the UI from this Runnable instead of in packet handlers
	 * because packets come in at high frequency -- 220Hz or more for raw EEG
	 * -- and it only makes sense to update the UI at about 60fps. The update
	 * functions do some string allocation, so this reduces our memory
	 * footprint and makes GC pauses less frequent/noticeable.
	 */
	private val tickUi = object : Runnable {
		override fun run() {
			if (eegStale) {
				updateEeg()
			}
			if (accelStale) {
				updateAccel()
			}
			if (alphaStale) {
				updateAlpha()
			}
			handler.postDelayed(this, (1000 / 60).toLong())
		}
	}


	//--------------------------------------
	// File I/O

	/**
	 * We don't want to block the UI thread while we write to a file, so the file
	 * writing is moved to a separate thread.
	 */
	private val fileThread = object : Thread() {
		override fun run() {
			Looper.prepare()
			fileHandler.set(Handler())
			val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
			val file = File(dir, "new_muse_file.muse")
			// MuseFileWriter will append to an existing file.
			// In this case, we want to start fresh so the file
			// if it exists.
			if (file.exists()) {
				file.delete()
			}
			Log.d(TAG, "Writing data to: " + file.absolutePath)
			fileWriter.set(MuseFileFactory.getMuseFileWriter(file))
			Looper.loop()
		}
	}
	// kt:


	//--------------------------------------
	// Lifecycle / Connection code


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val method = "onCreate()"

		Log.i(TAG, method)

		// We need to set the context on MuseManagerAndroid before we can do anything.
		// This must come before other LibMuse API calls as it also loads the library.
		manager = MuseManagerAndroid.getInstance()
		manager!!.setContext(this)

		Log.d(TAG, "LibMuse version=" + LibmuseVersion.instance().string)

		val weakActivity = WeakReference(this)

		// Register a listener to receive connection state changes.
		connectionListener = ConnectionListener(weakActivity)
		// Register a listener to receive data from a Muse.
		dataListener = DataListener(weakActivity)
		// Register a listener to receive notifications of what Muse headbands
		// we can connect to.
		manager!!.setMuseListener(MuseL(weakActivity))

		// Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
		// simplify the connection process.  This requires access to the COARSE_LOCATION
		// or FINE_LOCATION permissions.  Make sure we have these permissions before
		// proceeding.
		ensurePermissions()

		// Load and initialize our UI.
		initUI()

		// Start up a thread for asynchronous file operations.
		// This is only needed if you want to do File I/O.
		fileThread.start()

		// Start our asynchronous updates of the UI.
		handler.post(tickUi)

		// RB
		// Get the external storage state
		val state = Environment.getExternalStorageState()

		// Storage Directory
		var fileDir: File? = null

		// External vs. Local storage
		if (Environment.MEDIA_MOUNTED == state)
		// Get the external storage "/Download" directory path
			fileDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
		else
		// Get the local storage directory path
			fileDir = Environment.getDataDirectory()

		// Construct the test file path
		val sourceFile = File(fileDir, "test_muse_file.muse")

		// Check if the file exists
		if (sourceFile.exists()) {

			// Get the test file name
			val sourceFileName = sourceFile.name

			Log.w(method, "playing test file: $sourceFileName")

			// Set the flag
			fromFile = true

			val statusText = findViewById(R.id.con_status) as TextView
			statusText.text = ("Reading from test file — "
					+ sourceFileName +
					"\nMuse headband connections are disabled!\n")
			statusText.setTypeface(null, Typeface.BOLD_ITALIC)

			// Disable the buttons and spinner when reading from a file
			findViewById(R.id.refresh).isEnabled = false
			findViewById(R.id.connect).isEnabled = false
			findViewById(R.id.disconnect).isEnabled = false
			findViewById(R.id.pause).isEnabled = false
			findViewById(R.id.muses_spinner).isEnabled = false

			// file can be big, read it in a separate thread
			val playFileThread = Thread(Runnable { playMuseFile(sourceFile) })
			playFileThread.name = "File Player"
			playFileThread.start()
		} else {
			Log.w(method, "test file doesn't exist")

			// TODO
			// Check for faulty API
			//	API 25 (7.1.1)
			// has problem with this audioFeedbackThread
			if (!android.os.Build.VERSION.RELEASE.startsWith("7.1.")) {
				//kt:
				// start the audio feedback thread
				val audioFeedbackThread = Thread(Runnable {
					stopSounds = 0
					playAudioFeedback(0)
				})
				audioFeedbackThread.name = "Audio Feedback"
				audioFeedbackThread.start()
				// kt:
			}

		}
		// RB

		// TODO
		// Check for faulty APIs
		//	API 17 (4.2.2) &
		//	API 18 (4.3.1) &
		//	API 25 (7.1.1)
		//	have faulty Tone Generator support
		if (!android.os.Build.VERSION.RELEASE.startsWith("4.2.") &&
			!android.os.Build.VERSION.RELEASE.startsWith("4.3.") &&
			!android.os.Build.VERSION.RELEASE.startsWith("7.1.")) {
			// kt: initial audio test
			Log.d("Muse Headband", "sound test start")
			stopSounds = 0
			playAudioFeedback(1)
			Log.d("Muse Headband", "sound test done")
			// kt: end audio test
		}

	}    // end - onCreate()

	override fun onPause() {
		super.onPause()
		// It is important to call stopListening when the Activity is paused
		// to avoid a resource leak from the LibMuse library.
		manager!!.stopListening()
	}

	override fun onClick(v: View) {

		Log.i(TAG, "onClick()")

		if (v.id == R.id.refresh) {
			// The user has pressed the "Refresh" button.
			// Start listening for nearby or paired Muse headbands. We call stopListening
			// first to make sure startListening will clear the list of headbands and start fresh.
			manager!!.stopListening()
			manager!!.startListening()

		} else if (v.id == R.id.connect) {

			// The user has pressed the "Connect" button to connect to
			// the headband in the spinner.

			// Listening is an expensive operation, so now that we know
			// which headband the user wants to connect to we can stop
			// listening for other headbands.
			manager!!.stopListening()

			val availableMuses = manager!!.muses
			val musesSpinner = findViewById(R.id.muses_spinner) as Spinner

			// Check that we actually have something to connect to.
			if (availableMuses.size < 1 || musesSpinner.adapter.count < 1) {
				Log.d(TAG, "There is nothing to connect to")
			} else {

				// Cache the Muse that the user has selected.
				muse = availableMuses[musesSpinner.selectedItemPosition]
				// Unregister all prior listeners and register our data listener to
				// receive the MuseDataPacketTypes we are interested in.  If you do
				// not register a listener for a particular data type, you will not
				// receive data packets of that type.
				muse!!.unregisterAllListeners()
				muse!!.registerConnectionListener(connectionListener)
				muse!!.registerDataListener(dataListener, MuseDataPacketType.EEG)
				muse!!.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE)
				muse!!.registerDataListener(dataListener, MuseDataPacketType.ACCELEROMETER)
				muse!!.registerDataListener(dataListener, MuseDataPacketType.BATTERY)
				muse!!.registerDataListener(dataListener, MuseDataPacketType.DRL_REF)
				muse!!.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION)

				// Initiate a connection to the headband and stream the data asynchronously.
				muse!!.runAsynchronously()
			}

		} else if (v.id == R.id.disconnect) {

			// The user has pressed the "Disconnect" button.
			// Disconnect from the selected Muse.
			if (muse != null) {
				muse!!.disconnect()
			}

		} else if (v.id == R.id.pause) {

			// The user has pressed the "Pause/Resume" button to either pause or
			// resume data transmission.  Toggle the state and pause or resume the
			// transmission on the headband.
			if (muse != null) {
				dataTransmission = !dataTransmission
				muse!!.enableDataTransmission(dataTransmission)
			}
		}
	}

	//--------------------------------------
	// Permissions

	/**
	 * The ACCESS_COARSE_LOCATION permission is required to use the
	 * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
	 * On an Android 6.0 device, the following code will display 2 dialogs,
	 * one to provide context and the second to request the permission.
	 * On an Android device running an earlier version, nothing is displayed
	 * as the permission is granted from the manifest.
	 *
	 *
	 * If the permission is not granted, then Muse 2016 (MU-02) headbands will
	 * not be discovered and a SecurityException will be thrown.
	 */
	private fun ensurePermissions() {

		Log.i(TAG, "ensurePermissions()")

		if (ContextCompat.checkSelfPermission(this,
						Manifest.permission.ACCESS_COARSE_LOCATION) !== PackageManager.PERMISSION_GRANTED) {
			// We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
			// the user to grant us the permission.

			val buttonListener = DialogInterface.OnClickListener { dialog, which ->
				dialog.dismiss()
				ActivityCompat.requestPermissions(this@MainActivity,
						arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
						0)
			}

			// This is the context dialog which explains to the user the reason we are requesting
			// this permission.  When the user presses the positive (I Understand) button, the
			// standard Android permission dialog will be displayed (as defined in the button
			// listener above).
			val introDialog = AlertDialog.Builder(this)
					.setTitle(R.string.permission_dialog_title)
					.setMessage(R.string.permission_dialog_description)
					.setPositiveButton(R.string.permission_dialog_understand, buttonListener)
					.create()
			introDialog.show()
		}
	}


	//--------------------------------------
	// Listeners

	/**
	 * You will receive a callback to this method each time a headband is discovered.
	 * In this example, we update the spinner with the MAC address of the headband.
	 */
	fun museListChanged() {
		val list = manager!!.muses
		spinnerAdapter!!.clear()
		for (m in list) {
			spinnerAdapter!!.add(m.name + " - " + m.macAddress)
		}
	}

	/**
	 * You will receive a callback to this method each time there is a change to the
	 * connection state of one of the headbands.
	 *
	 * @param p    A packet containing the current and prior connection states
	 * @param muse The headband whose state changed.
	 */
	fun receiveMuseConnectionPacket(p: MuseConnectionPacket, muse: Muse) {

		Log.i(TAG, "receiveMuseConnectionPacket()")

		val current = p.currentConnectionState

		// Format a message to show the change of connection state in the UI.
		val status = p.previousConnectionState.toString() + " -> " + current
		Log.d(TAG, status)

		// kt:
		// 1.3.0 final String full = "Muse " + p.getSource().getMacAddress() +" " + status;
		val full = "Muse " + muse.macAddress + " " + status

		Log.d("Muse Headband kt", full)
		// kt:

		// Update the UI with the change in connection state.
		handler.post {
			val statusText = findViewById(R.id.con_status) as TextView
			statusText.text = status

			val museVersion = muse.museVersion
			val museVersionText = findViewById(R.id.version) as TextView
			// If we haven't yet connected to the headband, the version information
			// will be null.  You have to connect to the headband before either the
			// MuseVersion or MuseConfiguration information is known.
			if (museVersion != null) {
				val version = (museVersion.firmwareType + " - "
						+ museVersion.firmwareVersion + " - "
						+ museVersion.protocolVersion)
				museVersionText.text = version
			} else {
				museVersionText.setText(R.string.undefined)
			}
		}

		if (current == ConnectionState.DISCONNECTED) {
			Log.d(TAG, "Muse disconnected:" + muse.name)
			// Save the data file once streaming has stopped.
			saveFile()
			// We have disconnected from the headband, so set our cached copy to null.
			this.muse = null
		}
	}

	/**
	 * You will receive a callback to this method each time the headband sends a MuseDataPacket
	 * that you have registered.  You can use different listeners for different packet types or
	 * a single listener for all packet types as we have done here.
	 *
	 * @param p    The data packet containing the data from the headband (eg. EEG data)
	 * @param muse The headband that sent the information.
	 */
	fun receiveMuseDataPacket(p: MuseDataPacket, muse: Muse?) {
		writeDataPacketToFile(p)

		// valuesSize returns the number of data values contained in the packet.
		val n = p.valuesSize()

		if (fromFile) {
			when (p.packetType()) {
				MuseDataPacketType.EEG -> {
					assert(eegBuffer.size >= n)
					getEegChannelValues(eegBuffer, p)
					eegStale = true
				}
				MuseDataPacketType.ACCELEROMETER -> {
					assert(accelBuffer.size >= n)
					getAccelValues(p)
					accelStale = true
				}
				MuseDataPacketType.ALPHA_RELATIVE -> {
					assert(alphaBuffer.size >= n)
					getEegChannelValues(alphaBuffer, p)
					alphaStale = true
				}
				MuseDataPacketType.BATTERY,
				MuseDataPacketType.DRL_REF,
				MuseDataPacketType.QUANTIZATION -> {
				}
				else -> {
				}
			}
		} else {
			when (p.packetType()) {
				// kt:
				/*
				case EEG:
					assert(eegBuffer.length >= n);
					getEegChannelValues(eegBuffer,p);
					eegStale = true;
					break;
				*/
				// kt:
				MuseDataPacketType.ACCELEROMETER -> {
					assert(accelBuffer.size >= n)
					getAccelValues(p)
					accelStale = true
				}
				MuseDataPacketType.ALPHA_RELATIVE -> {
					assert(alphaBuffer.size >= n)
					getEegChannelValues(alphaBuffer, p)
					alphaStale = true
				}
				// kt:
				// 1.3.0 case HORSESHOE:
				MuseDataPacketType.HSI,
				MuseDataPacketType.EEG,
				MuseDataPacketType.ALPHA_ABSOLUTE,
					//case ALPHA_SCORE:
				MuseDataPacketType.BETA_ABSOLUTE,
					// case BETA_RELATIVE:
					//case BETA_SCORE:
				MuseDataPacketType.DELTA_ABSOLUTE,
					//case DELTA_RELATIVE:
					//case DELTA_SCORE:
				MuseDataPacketType.GAMMA_ABSOLUTE,
					//case GAMMA_RELATIVE:
					//case GAMMA_SCORE:
				MuseDataPacketType.THETA_ABSOLUTE ->
					//case THETA_RELATIVE:
					//case THETA_SCORE:
					// 1.3.0 handleWaivePacket(p, p.getValues());
					handleWaivePacket(p, p.values())
				// kt:
				MuseDataPacketType.BATTERY,
				MuseDataPacketType.DRL_REF,
				MuseDataPacketType.QUANTIZATION -> {
				}
				else -> {
				}
			}
		}
	}

	// kt:
	private fun handleWaivePacket(p: MuseDataPacket, data: ArrayList<Double>) //(MuseDataPacket p)
	{
		var elem1 = 0.0
		var elem2 = 0.0
		var elem3 = 0.0
		var elem4 = 0.0

		//final ArrayList<Double> data = p.getValues();
		val got_data = false
//		if (pkt_timestamp == 0L || pkt_timestamp == -1) {
		if (pkt_timestamp == 0L || pkt_timestamp.equals(-1)) {
			// 1.3.0 pkt_timestamp = p.getTimestamp();
			pkt_timestamp = p.timestamp()
			if (pkt_timestamp_ref == 0L) {
				pkt_timestamp_ref = pkt_timestamp
			}
		}
		val tstamp = Calendar.getInstance().timeInMillis
		if (data_time_stamp_ref == 0L)
			data_time_stamp_ref = tstamp //save start time

		// 1.3.0 switch (p.getPacketType())
		when (p.packetType()) {
			MuseDataPacketType.EEG -> {
				// 1.3.0 eegElem[0] = data.get(Eeg.TP9.ordinal())/1000; // divide by 1000 to scale with alpha absolute, beta etc. signals
				eegElem[0] = data[Eeg.EEG1.ordinal] / 1000 // divide by 1000 to scale with alpha absolute, beta etc. signals
				// 1.3.0 eegElem[1] = data.get(Eeg.FP1.ordinal())/1000;
				eegElem[1] = data[Eeg.EEG2.ordinal] / 1000
				// 1.3.0 eegElem[2] = data.get(Eeg.FP2.ordinal())/1000;
				eegElem[2] = data[Eeg.EEG3.ordinal] / 1000
				// 1.3.0 eegElem[3] = data.get(Eeg.TP10.ordinal())/1000;
				eegElem[3] = data[Eeg.EEG4.ordinal] / 1000
				got_data_mask = got_data_mask or EegAbsolute
			}
			MuseDataPacketType.ALPHA_ABSOLUTE -> {
				// 1.3.0 alphaAbsoluteElem[0] = data.get(Eeg.TP9.ordinal());
				alphaAbsoluteElem[0] = data[Eeg.EEG1.ordinal]
				// 1.3.0 alphaAbsoluteElem[1] = data.get(Eeg.FP1.ordinal());
				alphaAbsoluteElem[1] = data[Eeg.EEG2.ordinal]
				// 1.3.0 alphaAbsoluteElem[2] = data.get(Eeg.FP2.ordinal());
				alphaAbsoluteElem[2] = data[Eeg.EEG3.ordinal]
				// 1.3.0 alphaAbsoluteElem[3] = data.get(Eeg.TP10.ordinal());
				alphaAbsoluteElem[3] = data[Eeg.EEG4.ordinal]
				got_data_mask = got_data_mask or AlphaAbsolute
			}

			MuseDataPacketType.BETA_ABSOLUTE -> {
				// 1.3.0 betaAbsoluteElem[0] = data.get(Eeg.TP9.ordinal());
				betaAbsoluteElem[0] = data[Eeg.EEG1.ordinal]
				// 1.3.0 betaAbsoluteElem[1] = data.get(Eeg.FP1.ordinal());
				betaAbsoluteElem[1] = data[Eeg.EEG2.ordinal]
				// 1.3.0 betaAbsoluteElem[2] = data.get(Eeg.FP2.ordinal());
				betaAbsoluteElem[2] = data[Eeg.EEG3.ordinal]
				// 1.3.0 betaAbsoluteElem[3] = data.get(Eeg.TP10.ordinal());
				betaAbsoluteElem[3] = data[Eeg.EEG4.ordinal]
				got_data_mask = got_data_mask or BetaAbsolute
			}

			MuseDataPacketType.DELTA_ABSOLUTE -> {
				// 1.3.0 deltaAbsoluteElem[0] = data.get(Eeg.TP9.ordinal());
				deltaAbsoluteElem[0] = data[Eeg.EEG1.ordinal]
				// 1.3.0 deltaAbsoluteElem[1] = data.get(Eeg.FP1.ordinal());
				deltaAbsoluteElem[1] = data[Eeg.EEG2.ordinal]
				// 1.3.0 deltaAbsoluteElem[2] = data.get(Eeg.FP2.ordinal());
				deltaAbsoluteElem[2] = data[Eeg.EEG3.ordinal]
				// 1.3.0 deltaAbsoluteElem[3] = data.get(Eeg.TP10.ordinal());
				deltaAbsoluteElem[3] = data[Eeg.EEG4.ordinal]

				got_data_mask = got_data_mask or DeltaAbsolute
			}

			MuseDataPacketType.GAMMA_ABSOLUTE -> {
				// 1.3.0 gammaAbsoluteElem[0] = data.get(Eeg.TP9.ordinal());
				gammaAbsoluteElem[0] = data[Eeg.EEG1.ordinal]
				// 1.3.0 gammaAbsoluteElem[1] = data.get(Eeg.FP1.ordinal());
				gammaAbsoluteElem[1] = data[Eeg.EEG2.ordinal]
				// 1.3.0 gammaAbsoluteElem[2] = data.get(Eeg.FP2.ordinal());
				gammaAbsoluteElem[2] = data[Eeg.EEG3.ordinal]
				// 1.3.0 gammaAbsoluteElem[3] = data.get(Eeg.TP10.ordinal());
				gammaAbsoluteElem[3] = data[Eeg.EEG4.ordinal]
				got_data_mask = got_data_mask or GammaAbsolute
				// 1.3.0 thetaAbsoluteElem[0] = data.get(Eeg.TP9.ordinal());
				thetaAbsoluteElem[0] = data[Eeg.EEG1.ordinal]
				// 1.3.0 thetaAbsoluteElem[1] = data.get(Eeg.FP1.ordinal());
				thetaAbsoluteElem[1] = data[Eeg.EEG2.ordinal]
				// 1.3.0 thetaAbsoluteElem[2] = data.get(Eeg.FP2.ordinal());
				thetaAbsoluteElem[2] = data[Eeg.EEG3.ordinal]
				// 1.3.0 thetaAbsoluteElem[3] = data.get(Eeg.TP10.ordinal());
				thetaAbsoluteElem[3] = data[Eeg.EEG4.ordinal]
				got_data_mask = got_data_mask or ThetaAbsolute
			}

			MuseDataPacketType.THETA_ABSOLUTE -> {
				thetaAbsoluteElem[0] = data[Eeg.EEG1.ordinal]
				thetaAbsoluteElem[1] = data[Eeg.EEG2.ordinal]
				thetaAbsoluteElem[2] = data[Eeg.EEG3.ordinal]
				thetaAbsoluteElem[3] = data[Eeg.EEG4.ordinal]
				got_data_mask = got_data_mask or ThetaAbsolute
			}

			// 1.3.0 case HORSESHOE:
			MuseDataPacketType.HSI -> {
				//int helem1 = 0, helem2 = 0, helem3 = 0, helem4 = 0;
				updateHorseshoe(data)
				// 1.3.0 elem1 = data.get(Eeg.TP9.ordinal());
				elem1 = data[Eeg.EEG1.ordinal]
				// 1.3.0 elem2 = data.get(Eeg.FP1.ordinal());
				elem2 = data[Eeg.EEG2.ordinal]
				// 1.3.0 elem3 = data.get(Eeg.FP2.ordinal());
				elem3 = data[Eeg.EEG3.ordinal]
				// 1.3.0 elem4 = data.get(Eeg.TP10.ordinal());
				elem4 = data[Eeg.EEG4.ordinal]

				if (validSensor[0] == true)
					horseshoeElem[0] = elem1.toInt()
				if (validSensor[1] == true)
					horseshoeElem[1] = elem2.toInt()
				if (validSensor[2] == true)
					horseshoeElem[2] = elem3.toInt()
				if (validSensor[3] == true)
					horseshoeElem[3] = elem4.toInt()

				got_data_mask = got_data_mask or Horseshoe
				Log.i("kt: hrs data ", Integer.toString(waive_pkt_cnt))
			}
		}//int tone_on_duration = 400;
		//int tone_off_duration = 400;
		/*
			try
            {
            if(elem1 > 2)
            {
                ToneGenerator toneG1 = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                toneG1.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 100);
                Thread.sleep(tone_on_duration);
                toneG1.stopTone();
                Thread.sleep(tone_off_duration);
            }
            if(elem2 > 2)
            {
                ToneGenerator toneG2 = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                toneG2.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100);
                Thread.sleep(tone_on_duration);
                toneG2.stopTone();
                Thread.sleep(tone_off_duration);
            }
            if(elem3 > 2)
            {
                ToneGenerator toneG3 = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                toneG3.startTone(ToneGenerator.TONE_PROP_BEEP, 50);
                Thread.sleep(tone_on_duration);
                toneG3.stopTone();
                Thread.sleep(tone_off_duration);
            }
            if(elem4 > 2)
            {
                ToneGenerator toneG4 = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                toneG4.startTone(ToneGenerator.TONE_PROP_BEEP2, 50);
                Thread.sleep(tone_on_duration);
                toneG4.stopTone();
                Thread.sleep(tone_off_duration);
            }
            } // end try
            catch(Exception e){}
            *//*
            String strData = tstamp-data_time_stamp_ref + ",,,,," +
                Integer.toHexString(helem1).toUpperCase() + "," +
                Integer.toHexString(helem2).toUpperCase() + "," +
                Integer.toHexString(helem3).toUpperCase() + "," +
                Integer.toHexString(helem4).toUpperCase() + "\r\n";
            print_line.printf(strData);
            */

		// write/append data to file
		if (got_data_mask == AllDataMask) {
			val cur_tstamp = tstamp - data_time_stamp_ref
			val cur_pkt_tstamp = pkt_timestamp - pkt_timestamp_ref
			val strData = cur_pkt_tstamp.toString() + "," + cur_tstamp + "," +
					eegElem[0] + "," + eegElem[3] + "," + eegElem[1] + "," + eegElem[2] + "," +
					alphaAbsoluteElem[0] + "," + alphaAbsoluteElem[3] + "," + alphaAbsoluteElem[1] + "," + alphaAbsoluteElem[2] + "," +
					betaAbsoluteElem[0] + "," + betaAbsoluteElem[3] + "," + betaAbsoluteElem[1] + "," + betaAbsoluteElem[2] + "," +
					deltaAbsoluteElem[0] + "," + deltaAbsoluteElem[3] + "," + deltaAbsoluteElem[1] + "," + deltaAbsoluteElem[2] + "," +
					gammaAbsoluteElem[0] + "," + gammaAbsoluteElem[3] + "," + gammaAbsoluteElem[1] + "," + gammaAbsoluteElem[2] + "," +
					thetaAbsoluteElem[0] + "," + thetaAbsoluteElem[3] + "," + thetaAbsoluteElem[1] + "," + thetaAbsoluteElem[2] + "," +
					horseshoeElem[0] + "," + horseshoeElem[3] + "," + horseshoeElem[1] + "," + horseshoeElem[2] + "\r\n"

			pkt_timestamp = 0 // for the next time
			waive_pkt_cnt++ // for debug
			//if(waive_pkt_cnt == 5) //for debug
			//    waive_pkt_cnt = 0;
			//if(elem1!= 0 && elem2!=0 && elem3!=0 && elem4!=0)
			run {
				print_line!!.printf(strData)
				got_data_mask = 0
				data_set_cnt++
				//if (writeData.getBufferedMessagesSize() > 8096)
				if (data_set_cnt == 5)
				// tune the final number
				{
					//Log.i("Muse packet timestamp=",  String.valueOf(muse_tstamp));
					data_set_cnt = 0
					try {
						writeData!!.flush()
					} catch (e: Exception) {
						Log.e("Muse Headband exception", e.toString())
					}

				}
			}
		}
	}
	// kt:

	// kt:
	private fun updateHorseshoe(data: ArrayList<Double>) {

		//Activity activity = activityRef.get();
		val activity = this.applicationContext as Activity

		activity?.runOnUiThread {
			//TextView tp9 = (TextView) findViewById(R.id.eeg_tp9);
			// 1.3.0 TextView fp1 = (TextView) findViewById(R.id.eeg_fp1);
			val fp1 = findViewById(R.id.eeg_af7) as TextView
			// 1.3.0 TextView fp2 = (TextView) findViewById(R.id.eeg_fp2);
			val fp2 = findViewById(R.id.eeg_af8) as TextView
			//TextView tp10 = (TextView) findViewById(R.id.eeg_tp10);
			//tp9.setText(String.format("%6.2f", data.get(Eeg.TP9.ordinal())));
			// 1.3.0 fp1.setText(String.format("%6.2f", data.get(Eeg.FP1.ordinal())));
			fp1.text = String.format("%6.2f", data[Eeg.EEG2.ordinal])
			// 1.3.0 fp2.setText(String.format("%6.2f", data.get(Eeg.FP2.ordinal())));
			fp2.text = String.format("%6.2f", data[Eeg.EEG3.ordinal])
			//tp10.setText(String.format("%6.2f", data.get(Eeg.TP10.ordinal())));
		}
	}
	// kt:


	/**
	 * You will receive a callback to this method each time an artifact packet is generated if you
	 * have registered for the ARTIFACTS data type.  MuseArtifactPackets are generated when
	 * eye blinks are detected, the jaw is clenched and when the headband is put on or removed.
	 *
	 * @param p    The artifact packet with the data from the headband.
	 * @param muse The headband that sent the information.
	 */
	fun receiveMuseArtifactPacket(p: MuseArtifactPacket, muse: Muse) {}

	/**
	 * Helper methods to get different packet values.  These methods simply store the
	 * data in the buffers for later display in the UI.
	 *
	 *
	 * getEegChannelValue can be used for any EEG or EEG derived data packet type
	 * such as EEG, ALPHA_ABSOLUTE, ALPHA_RELATIVE or HSI_PRECISION.  See the documentation
	 * of MuseDataPacketType for all of the available values.
	 * Specific packet types like ACCELEROMETER, GYRO, BATTERY and DRL_REF have their own
	 * getValue methods.
	 */
	private fun getEegChannelValues(buffer: DoubleArray, p: MuseDataPacket) {
		buffer[0] = p.getEegChannelValue(Eeg.EEG1)
		buffer[1] = p.getEegChannelValue(Eeg.EEG2)
		buffer[2] = p.getEegChannelValue(Eeg.EEG3)
		buffer[3] = p.getEegChannelValue(Eeg.EEG4)
		buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT)
		buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT)
	}

	private fun getAccelValues(p: MuseDataPacket) {
		accelBuffer[0] = p.getAccelerometerValue(Accelerometer.X)
		accelBuffer[1] = p.getAccelerometerValue(Accelerometer.Y)
		accelBuffer[2] = p.getAccelerometerValue(Accelerometer.Z)
	}


	//--------------------------------------
	// UI Specific methods

	/**
	 * Initializes the UI of the example application.
	 */
	private fun initUI() {
		setContentView(R.layout.activity_main)
		val refreshButton = findViewById(R.id.refresh) as Button
		refreshButton.setOnClickListener(this)
		val connectButton = findViewById(R.id.connect) as Button
		connectButton.setOnClickListener(this)
		val disconnectButton = findViewById(R.id.disconnect) as Button
		disconnectButton.setOnClickListener(this)
		val pauseButton = findViewById(R.id.pause) as Button
		pauseButton.setOnClickListener(this)

		spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item)
		val musesSpinner = findViewById(R.id.muses_spinner) as Spinner
		musesSpinner.adapter = spinnerAdapter
	}

	/**
	 * The following methods update the TextViews in the UI with the data
	 * from the buffers.
	 */
	private fun updateAccel() {
		val acc_x = findViewById(R.id.acc_x) as TextView
		val acc_y = findViewById(R.id.acc_y) as TextView
		val acc_z = findViewById(R.id.acc_z) as TextView
		acc_x.text = String.format("%6.2f", accelBuffer[0])
		acc_y.text = String.format("%6.2f", accelBuffer[1])
		acc_z.text = String.format("%6.2f", accelBuffer[2])
	}

	private fun updateEeg() {
		val tp9 = findViewById(R.id.eeg_tp9) as TextView
		val fp1 = findViewById(R.id.eeg_af7) as TextView
		val fp2 = findViewById(R.id.eeg_af8) as TextView
		val tp10 = findViewById(R.id.eeg_tp10) as TextView
		tp9.text = String.format("%6.2f", eegBuffer[0])
		fp1.text = String.format("%6.2f", eegBuffer[1])
		fp2.text = String.format("%6.2f", eegBuffer[2])
		tp10.text = String.format("%6.2f", eegBuffer[3])
	}

	private fun updateAlpha() {
		val elem1 = findViewById(R.id.elem1) as TextView
		elem1.text = String.format("%6.2f", alphaBuffer[0])
		val elem2 = findViewById(R.id.elem2) as TextView
		elem2.text = String.format("%6.2f", alphaBuffer[1])
		val elem3 = findViewById(R.id.elem3) as TextView
		elem3.text = String.format("%6.2f", alphaBuffer[2])
		val elem4 = findViewById(R.id.elem4) as TextView
		elem4.text = String.format("%6.2f", alphaBuffer[3])
	}

	/**
	 * Writes the provided MuseDataPacket to the file.  MuseFileWriter knows
	 * how to write all packet types generated from LibMuse.
	 *
	 * @param p The data packet to write.
	 */
	private fun writeDataPacketToFile(p: MuseDataPacket) {
		val h = fileHandler.get()
		h?.post { fileWriter.get().addDataPacket(0, p) }
	}

	/**
	 * Flushes all the data to the file and closes the file writer.
	 */
	private fun saveFile() {
		val h = fileHandler.get()
		h?.post {
			val w = fileWriter.get()
			// Annotation strings can be added to the file to
			// give context as to what is happening at that point in
			// time.  An annotation can be an arbitrary string or
			// may include additional AnnotationData.
			w.addAnnotationString(0, "Disconnected")
			w.flush()
			w.close()
		}
	}

	// kt:
	private fun playAudioFeedback(at_mode: Int) {
		val tone: Int
		val play_tone = 0
		var i = 0
		var k: Int

		// tone test
		var j: Int
		if (at_mode == 1) {
			j = 0
			while (j < HorseshoeElemeToneMax) {
				k = 0
				while (k < 2)
				//play twice
				{
					musePlayTone(null, j)
					k++
				}
				j++
			} // horseshoe for loop
			return  // test done
		}
		// end tone test


		// normal operation
		Log.i("Muse Headband kt:", "Starting ToneGenerator")
		var toneG: ToneGenerator? = ToneGenerator(AudioManager.STREAM_ALARM, 100)

		// infinite - user can connect/disconnect multiple times without exiting the app
		while (true) {
			if (stopSounds != 0) {
				toneG!!.stopTone()
				toneG = null
				Log.i("Muse Headband kt:", "Stopping ToneGenerator")
				//stopSounds = 0;
				// put thread to sleep
				try {
					Thread.sleep(1000) //let CPU rest
				} catch (e: Exception) {
				}

				continue
			}

			if (horseshoeElemeTone != HorseshoeElemeToneMax) {
				i = horseshoeElemeTone
			}
			// do not play sounds if horseshoe is less than 3
			// look for horseshoe less than 3
			while (horseshoeElem[i] < 3) {
				i++
				if (i == HorseshoeElemeToneMax)
					break
			}
			// we are here either becasue we scanned to the end of the array
			// or we found a bad sensor
			if (i != HorseshoeElemeToneMax)
			// if none bad, no sound
			{
				// we had bad sensor, play its sound
				musePlayTone(toneG, i)
			} else {
				horseshoeElemeTone = 0 //restart scanning from the start of array
			}
			// put thread to sleep
			try {
				Thread.sleep(1000) //let CPU rest
			} catch (e: Exception) {
			}

		} // infinite loop
	}
	// kt:


	// smf
	private fun playMuseFile(fileName: File) {

		val tag = "playMuseFile()"

		if (!fileName.exists()) {
			Log.w(tag, "file doesn't exist")
			return
		}

		// Get a file reader
		val fileReader = MuseFileFactory.getMuseFileReader(fileName)

		// Loop through the packages in the file
		var res = fileReader.gotoNextMessage()
		while (res.level == ResultLevel.R_INFO && !res.info.contains("EOF")) {
			val type = fileReader.messageType
			val id = fileReader.messageId
			val timestamp = fileReader.messageTimestamp
			Log.d(tag, "type: " + type.toString() +
					" id: " + Integer.toString(id) +
					" timestamp: " + timestamp.toString())
			when (type) {
				MessageType.EEG, MessageType.BATTERY, MessageType.ACCELEROMETER, MessageType.QUANTIZATION -> {
					val packet = fileReader.dataPacket
					Log.d(tag, "data packet: " + packet.packetType().toString())
					dataListener!!.receiveMuseDataPacket(packet, muse)
				}
				MessageType.VERSION -> {
					val museVersion = fileReader.version
					val version = museVersion.firmwareType +
							" - " + museVersion.firmwareVersion +
							" - " + Integer.toString(
							museVersion.protocolVersion)
					Log.d(tag, "version $version")
					val activity = dataListener!!.activityRef.get()
					// UI thread is used here only because we need to update
					// TextView values. You don't have to use another thread, unless
					// you want to run disconnect() or connect() from connection packet
					// handler. In this case creating another thread is required.
					activity?.runOnUiThread {
						val museVersionText = findViewById(R.id.version) as TextView
						museVersionText.text = version
					}
				}
				MessageType.CONFIGURATION -> {
					val config = fileReader.configuration
					Log.d(tag, "config " + config.bluetoothMac)
				}
				MessageType.ANNOTATION -> {
					val annotation = fileReader.annotation
					Log.d(tag, "annotationData " + annotation.data)
					Log.d(tag, "annotationFormat " + annotation.format.toString())
					Log.d(tag, "annotationEventType " + annotation.eventType)
					Log.d(tag, "annotationEventId " + annotation.eventId)
					Log.d(tag, "annotationParentId " + annotation.parentId)
				}
				else -> {
				}
			}

			// Read the next message.
			res = fileReader.gotoNextMessage()
		}
	}
	// smf

	/**
	 * Reads the provided .muse file and prints the data to the logcat.
	 *
	 * @param name The name of the file to read.  The file in this example
	 * is assumed to be in the Environment.DIRECTORY_DOWNLOADS
	 * directory.
	 */
	private fun playMuseFile(name: String) {

		val tag = "playMuseFile()"

		// Get the "/DOWNLOAD" directory path
		val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
		val file = File(dir, name)

		if (!file.exists()) {
			Log.w(tag, "file doesn't exist")
			return
		}

		// Define a file reader
		val fileReader = MuseFileFactory.getMuseFileReader(file)

		// Loop through each message in the file.  gotoNextMessage will read the next message
		// and return the result of the read operation as a Result.
		var res = fileReader.gotoNextMessage()
		while (res.level == ResultLevel.R_INFO && !res.info.contains("EOF")) {

			val type = fileReader.messageType
			val id = fileReader.messageId
			val timestamp = fileReader.messageTimestamp

			Log.i(tag, "type: " + type.toString() +
					" id: " + Integer.toString(id) +
					" timestamp: " + timestamp.toString())

			when (type) {
				// EEG messages contain raw EEG data or DRL/REF data.
				// EEG derived packets like ALPHA_RELATIVE and artifact packets
				// are stored as MUSE_ELEMENTS messages.
				MessageType.EEG, MessageType.BATTERY, MessageType.ACCELEROMETER, MessageType.QUANTIZATION, MessageType.GYRO, MessageType.MUSE_ELEMENTS -> {
					val packet = fileReader.dataPacket
					Log.i(tag, "data packet: " + packet.packetType().toString())
				}
				MessageType.VERSION -> {
					val version = fileReader.version
					Log.i(tag, "version: " + version.firmwareType)
				}
				MessageType.CONFIGURATION -> {
					val config = fileReader.configuration
					Log.i(tag, "config: " + config.bluetoothMac)
				}
				MessageType.ANNOTATION -> {
					val annotation = fileReader.annotation
					Log.i(tag, "annotation: " + annotation.data)
				}
				else -> {
				}
			}

			// Read the next message.
			res = fileReader.gotoNextMessage()
		}
	}

	//--------------------------------------
	// Listener translators
	//
	// Each of these classes extend from the appropriate listener and contain a weak reference
	// to the activity.  Each class simply forwards the messages it receives back to the Activity.
	internal inner class MuseL(val activityRef: WeakReference<MainActivity>) : MuseListener() {

		override fun museListChanged() {
//			activityRef.get().museListChanged()
			activityRef.get()?.museListChanged()
		}
	}

	internal inner class ConnectionListener(val activityRef: WeakReference<MainActivity>) : MuseConnectionListener() {

		override fun receiveMuseConnectionPacket(p: MuseConnectionPacket, muse: Muse) {
//			activityRef.get().receiveMuseConnectionPacket(p, muse)
			activityRef.get()?.receiveMuseConnectionPacket(p, muse)
		}
	}

	internal inner class DataListener(val activityRef: WeakReference<MainActivity>) : MuseDataListener() {

		override fun receiveMuseDataPacket(p: MuseDataPacket, muse: Muse?) {
//			activityRef.get().receiveMuseDataPacket(p, muse)
			activityRef.get()?.receiveMuseDataPacket(p, muse)
		}

		override fun receiveMuseArtifactPacket(p: MuseArtifactPacket, muse: Muse) {
//			activityRef.get().receiveMuseArtifactPacket(p, muse)
			activityRef.get()?.receiveMuseArtifactPacket(p, muse)
		}
	}

	// kt: new stuff
	private fun musePlayTone(paramToneGenerator: ToneGenerator?, toneIdx: Int) {
		var paramToneGenerator = paramToneGenerator
		if (validSensor[toneIdx] == false) {
			return
		}

		if (paramToneGenerator == null) {
			paramToneGenerator = ToneGenerator(4, 100)
		}
		while (true) {
			paramToneGenerator.startTone(HorseshoeTones[toneIdx])
			try {
				Thread.sleep((this.tone_on_duration / 8).toLong())
				paramToneGenerator.stopTone()
				try {
					Thread.sleep((this.tone_off_duration / 2).toLong())
					return
				} catch (e: Exception) {
					Log.e("Muse Headband exception", e.toString())
				}

			} catch (e: Exception) {
				Log.e("Muse Headband exception", e.toString())
			}

		}
	}

	companion object {
		//int artifactsElem = 0;

		// bit masks for packet types:
		val AlphaAbsolute = 1
		val BetaAbsolute = 2
		val DeltaAbsolute = 4
		val GammaAbsolute = 8
		val ThetaAbsolute = 0x10
		val EegAbsolute = 0x20
		val Horseshoe = 0x80
		//public static final int Artifacts    = 0x40;

		val AllDataMask = AlphaAbsolute or
				BetaAbsolute or
				DeltaAbsolute or
				GammaAbsolute or
				ThetaAbsolute or
				EegAbsolute or
				Horseshoe
		val HorseshoeElemeToneMax = 4 // since there 4 sensors here, start with "completed playing all sounds" value
		val validSensor = booleanArrayOf(false, true, true, false)
		val HorseshoeTones = intArrayOf(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE,
										ToneGenerator.TONE_PROP_BEEP2,
										ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE,
										//ToneGenerator.TONE_PROP_BEEP,
										ToneGenerator.TONE_CDMA_ABBR_INTERCEPT)
	}
	// kt:

}
