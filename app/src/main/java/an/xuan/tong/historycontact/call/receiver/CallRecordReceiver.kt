package com.aykuttasil.callrecord.receiver

import an.xuan.tong.historycontact.Constant
import an.xuan.tong.historycontact.Utils.CurrentTime
import an.xuan.tong.historycontact.api.ApiService
import an.xuan.tong.historycontact.api.Repository
import an.xuan.tong.historycontact.api.model.InformationResponse
import an.xuan.tong.historycontact.api.model.CallLogServer
import an.xuan.tong.historycontact.call2.Utils
import an.xuan.tong.historycontact.call.CallRecord
import an.xuan.tong.historycontact.call.helper.PrefsHelper
import an.xuan.tong.historycontact.call.receiver.PhoneCallReceiver
import an.xuan.tong.historycontact.call2.ProcessingBase
import an.xuan.tong.historycontact.call2.RecorderFactory
import an.xuan.tong.historycontact.location.LocationCurrent
import an.xuan.tong.historycontact.realm.CachingCallLog
import an.xuan.tong.historycontact.realm.HistoryContactConfiguration
import an.xuan.tong.historycontact.realm.RealmUtils
import android.content.Context
import android.media.AudioFormat
import android.media.MediaRecorder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.collections.HashMap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import com.facebook.accountkit.internal.AccountKitController.getApplicationContext
import net.callrec.library.fix.RecorderHelper
import net.callrec.library.recorder.AudioRecorder
import net.callrec.library.recorder.base.RecorderBase


/**
 * Created by aykutasil on 19.10.2016.
 */
class CallRecordReceiver : PhoneCallReceiver {

    protected var recorder2: AudioRecorder? = null
    protected val recorderRun = RecorderRunnable()
    lateinit var recHandler: Handler
    protected var recordingStartedFlag: Boolean = false

    protected var phoneNumber: String = ""
    protected var typeCall: Int = -1

    protected var formatFile: String = ""
    protected var typeRecorder: ProcessingBase.TypeRecorder? = null
    protected var audioSource = -1
    protected var outputFormat: Int = 0
    protected var encoder: Int = 0
    protected var stereoChannel: Boolean = false
    protected var samplingRate: Int = 0
    protected var audioEncodingBitRate: Int = 0
    protected var filePathNoFormat: String = ""
    lateinit var context: Context

    protected lateinit var callRecord: CallRecord
    private var audiofile: File? = null
    private var isRecordStarted = false
    var startTime = 0L

    constructor()

    constructor(callRecord: CallRecord) {
        this.callRecord = callRecord
        recHandler = Handler()
    }

    override fun onIncomingCallReceived(context: Context, number: String, start: Date) {
        this.context = context
    }

    override fun onIncomingCallAnswered(context: Context, number: String, start: Date) {
        Log.e("antx", "call onIncomingCallAnswered")
        // startRecord(context, "incoming", number)
      //  startRecord(0)


    }

    override fun onIncomingCallEnded(context: Context, number: String, start: Date, end: Date) {
        Log.e("antx", "call onIncomingCallEnded")
//        stopRecord(context, number, start, end, false)
        this.context = context
      //  stopRecord()

    }

    override fun onOutgoingCallStarted(context: Context, number: String, start: Date) {
        //startRecord(context, "outgoing", number)
        this.context = context
       // startRecord(0)

        Log.e("antx", "call onOutgoingCallStarted")
    }

    override fun onOutgoingCallEnded(context: Context, number: String, start: Date, end: Date) {
        Log.e("antx", "call onOutgoingCallEnded")
        // stopRecord(context, number, start, end, true)
      //  stopRecord()

    }

    override fun onMissedCall(context: Context, number: String, start: Date) {
        Log.e("antx", "call onMissedCall")
        var dateStop = CurrentTime.getLocalTime()
        insertCall(number, dateStop.toString(), (0).toString(), "", null, "")
    }

    // Derived classes could override these to respond to specific events of interest
    protected fun onRecordingStarted(context: Context, callRecord: CallRecord, audioFile: File?) {
        Log.e("antx", "call onRecordingStarted")
    }

    protected fun onRecordingFinished(context: Context, callRecord: CallRecord, audioFile: File?) {
        Log.e("antx", "call onRecordingFinished")
    }

    private fun startRecord(context: Context, seed: String, phoneNumber: String) {
        try {
            val audioManager: AudioManager = getApplicationContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true

            val isSaveFile = PrefsHelper.readPrefBool(context, CallRecord.PREF_SAVE_FILE)
            Log.i(TAG, "isSaveFile: $isSaveFile")

            // dosya kayıt edilsin mi?
            if (!isSaveFile) {
                return
            }

            if (isRecordStarted) {
                try {
                    recorder!!.stop()  // stop the recording
                } catch (e: RuntimeException) {
                    // RuntimeException is thrown when stop() is called immediately after start().
                    // In this case the output file is not properly constructed ans should be deleted.
                    Log.d(TAG, "RuntimeException: stop() is called immediately after start()")

                    audiofile!!.delete()
                }

                releaseMediaRecorder()
                isRecordStarted = false
            } else {
                if (prepareAudioRecorder(context, seed, phoneNumber)) {
                    recorder!!.start()
                    isRecordStarted = true
                    onRecordingStarted(context, callRecord, audiofile)
                    startTime = CurrentTime.getLocalTime()
                    Log.i(TAG, "record start")
                } else {
                    releaseMediaRecorder()
                }
                //new MediaPrepareTask().execute(null, null, null);
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            releaseMediaRecorder()
        } catch (e: RuntimeException) {
            e.printStackTrace()
            releaseMediaRecorder()
        } catch (e: Exception) {
            e.printStackTrace()
            releaseMediaRecorder()
        }

    }

    private fun stopRecord(context: Context, number: String, start: Date, end: Date, isOutGoingCall: Boolean) {
        try {

            if (recorder != null && isRecordStarted) {
                releaseMediaRecorder()
                isRecordStarted = false
                onRecordingFinished(context, callRecord, audiofile)
                audiofile?.path?.let {
                    val file = File(it)
                    Log.e("antx", "path " + it + " name: " + file.name + " start" + start + "end: " + end)
                    sendRecoderToServer(it, number, start, end, isOutGoingCall)
                }

                Log.i(TAG, "record stop")
            }
        } catch (e: Exception) {
            releaseMediaRecorder()
            e.printStackTrace()
        }

    }

    private fun prepareAudioRecorder(context: Context, seed: String, phoneNumber: String): Boolean {
        try {
            var file_name = PrefsHelper.readPrefString(context, CallRecord.PREF_FILE_NAME)
            val dir_path = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_PATH)
            val dir_name = PrefsHelper.readPrefString(context, CallRecord.PREF_DIR_NAME)
            val show_seed = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_SEED)
            val show_phone_number = PrefsHelper.readPrefBool(context, CallRecord.PREF_SHOW_PHONE_NUMBER)
            val output_format = PrefsHelper.readPrefInt(context, CallRecord.PREF_OUTPUT_FORMAT)
            val audio_source = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_SOURCE)
            val audio_encoder = PrefsHelper.readPrefInt(context, CallRecord.PREF_AUDIO_ENCODER)

            val sampleDir = File("$dir_path/$dir_name")
            if (!sampleDir.exists()) {
                sampleDir.mkdirs()
            }

            val fileNameBuilder = StringBuilder()
            fileNameBuilder.append(file_name)
            fileNameBuilder.append("")

            if (show_seed) {
                fileNameBuilder.append(seed)
                fileNameBuilder.append("")
            }

            if (show_phone_number) {
                fileNameBuilder.append(phoneNumber)
                fileNameBuilder.append("")
            }


            file_name = fileNameBuilder.toString()

            var suffix = ""
            when (output_format) {
                MediaRecorder.OutputFormat.AMR_NB -> {
                    suffix = ".amr"
                }
                MediaRecorder.OutputFormat.AMR_WB -> {
                    suffix = ".amr"
                }
                MediaRecorder.OutputFormat.MPEG_4 -> {
                    suffix = ".mp4"
                }
                MediaRecorder.OutputFormat.THREE_GPP -> {
                    suffix = ".3gp"
                }
                else -> {
                    suffix = ".mp4"
                }
            }

            audiofile = File.createTempFile(file_name, suffix, sampleDir)

            recorder = MediaRecorder()
            recorder!!.setAudioSource(audio_source)
            recorder!!.setOutputFormat(output_format)
            /* recorder!!.setAudioEncodingBitRate(16)
             recorder!!.setAudioSamplingRate(44100)*/
            recorder!!.setAudioEncoder(audio_encoder)
            recorder!!.setOutputFile(audiofile!!.absolutePath)
            recorder!!.setOnErrorListener { mediaRecorder, i, i1 -> }


            try {
                recorder!!.prepare()
            } catch (e: IllegalStateException) {
                Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.message)
                releaseMediaRecorder()
                return false
            } catch (e: IOException) {
                Log.d(TAG, "IOException preparing MediaRecorder: " + e.message)
                releaseMediaRecorder()
                return false
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    private fun releaseMediaRecorder() {
        if (recorder != null) {
            recorder!!.reset()
            recorder!!.release()
            recorder = null

        }
    }

    companion object {
        private val TAG = "CallRecordReceiver"
        val ACTION_IN = "android.intent.action.PHONE_STATE"
        val ACTION_OUT = "android.intent.action.NEW_OUTGOING_CALL"
        val EXTRA_PHONE_NUMBER = "android.intent.extra.PHONE_NUMBER"
        private var recorder: MediaRecorder? = null
    }

    private fun sendRecoderToServer(filePath: String, number: String, startDate: Date, endDate: Date, typeCall: Boolean) {
        try {
            val file = File(filePath)
            val result: HashMap<String, String> = HashMap()
            result["Authorization"] = RealmUtils.getAuthorization()
            var id = RealmUtils.getAccountId()
            val temp = RequestBody.create(MediaType.parse("multipart/form-data"), file)
            var imageFile = MultipartBody.Part.createFormData(file.name, file.name, temp)
            Repository.createService(ApiService::class.java, result).insertUpload(Constant.KEY_API, id, imageFile)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                if (result.isNotEmpty()) {
                                    var diffInMs = endDate.time - startDate.time
                                    var diffInSec = diffInMs / 1000
                                    var dateStop = CurrentTime.getLocalTime()
                                    insertCall(number, dateStop.toString(), (diffInSec).toString(), result[0], typeCall, filePath)
                                }
                            },
                            { e ->
                                var diffInMs = endDate.time - startDate.time
                                var diffInSec = diffInMs / 1000
                                var dateStop = CurrentTime.getLocalTime()
                                insertCall(number, dateStop.toString(), (diffInSec).toString(), filePath, typeCall, filePath)
                            })
        } catch (e: Exception) {
            Log.e("antx Exception", "sendRcoderToServer " + e.message)
        }

    }

    private fun insertCall(phoneNunber: String?, datecreate: String, duration: String, fileaudio: String, type: Boolean? = null, file_path: String? = "") {
        val result: HashMap<String, String> = HashMap()
        result["Authorization"] = RealmUtils.getAuthorization()
        var id = RealmUtils.getAccountId()
        val mRealm = Realm.getInstance(HistoryContactConfiguration.createBuilder().build())
        mRealm.beginTransaction()
        var size = mRealm.where(LocationCurrent::class.java).findAll().size
        val locationCurrentRealm = mRealm.where(LocationCurrent::class.java).contains("idCurrent", Constant.KEY_LOCATION_CURRENT).findFirst()
        var locationCurrent: LocationCurrent? = locationCurrentRealm
        mRealm.commitTransaction()
        var message = CallLogServer(id, phoneNunber,
                datecreate, duration, locationCurrent?.lat, locationCurrent?.log, fileaudio, type.toString())
        id?.let {
            Repository.createService(ApiService::class.java, result).insertCallLog(message.toMap(), Constant.KEY_API)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { _ ->

                                /*try {
                                    val fdelete = File(file_path)
                                    fdelete.delete()
                                } catch (e: Exception) {

                                }*/

                            },
                            { e ->
                                RealmUtils.saveCallLogFail(CachingCallLog(RealmUtils.idAutoIncrement(CachingCallLog::class.java), idAccount = id, phone = phoneNunber,
                                        datecreate = datecreate, duration = duration, lat = locationCurrent?.lat, lng = locationCurrent?.log, fileaudio = fileaudio, type = type.toString()))
                                Log.e("antx", "saveCallLogFail  " + e.message)
                            })
        }
    }

    private fun convertJsonToObject(json: String?): InformationResponse {
        return Gson().fromJson(json, object : TypeToken<InformationResponse?>() {}.type)
    }


    //new call
    inner class RecorderRunnable : Runnable {
        override fun run() {
            try {
                startRecorder()
            } catch (e: RecorderBase.RecorderException) {
                e.printStackTrace()
            } catch (e: ProcessingBase.ProcessingException) {
                e.printStackTrace()
            }
        }
    }

    protected open fun startRecord(delayMS: Int) {
        recHandler.removeCallbacks(recorderRun)

        onPreStartRecord()

        if (delayMS == 0) {
            recHandler.post(recorderRun)
        } else {
            recHandler.postDelayed(recorderRun, delayMS.toLong())
            onWaitStartRecord()
        }
    }

    open protected fun onCheckRulesRecord(check: Boolean) {}
    open protected fun onWaitStartRecord() {}
    open protected fun onStartRecord() {}
    open protected fun onStopRecord() {}
    open protected fun onRecorderError(e: Exception) {}
    open protected fun onRecorderError(e: RecorderBase.RecorderException) {}

    open protected fun onRecorderError(e: ProcessingBase.ProcessingException) {}

    open protected fun onPreStartRecord() {}

    protected open fun stopRecord() {
        recHandler.removeCallbacks(recorderRun)
        stopRecorder()
    }

    private fun startRecorder() {
        val recorderHelper = RecorderHelper.getInstance()
        var startFixWavFormat = false

        makeOutputFile(context)
        prepareAudioPreferences()

        when (typeRecorder) {
            ProcessingBase.TypeRecorder.WAV -> {
                val channelConfig = if (stereoChannel) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                recorder2 = RecorderFactory.createWavRecorder(audioSource, samplingRate, channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT, filePathNoFormat)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    recorderHelper.startFixCallRecorder(context, recorder2!!.audioSessionId)
                    startFixWavFormat = true
                }
            }

            ProcessingBase.TypeRecorder.WAV_NATIVE -> {
                val channelConfig = if (stereoChannel) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                recorder2 = RecorderFactory.createNativeWavRecorder(audioSource, samplingRate, channelConfig,
                        AudioFormat.ENCODING_PCM_16BIT, filePathNoFormat)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    recorderHelper.startFixCallRecorder7(context)
                    startFixWavFormat = true
                }
            }
        }

        recorder2!!.start()

        recordingStartedFlag = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && startFixWavFormat) {
            recorderHelper.stopFixCallRecorder()
        }
    }

    fun makeOutputFile(context: Context): String {
        val dirStorage = Utils.getDefaultPath(context)

        val file = File(dirStorage)

        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw Exception()
            }
        }

        filePathNoFormat = dirStorage + Utils.makeFileName()
        return filePathNoFormat
    }

    fun prepareAudioPreferences() {
        formatFile = "wav"
        audioSource = MediaRecorder.AudioSource.MIC
        outputFormat = 0
        encoder = 0
        stereoChannel = false
        samplingRate = 8000
        audioEncodingBitRate = 0
        typeRecorder = ProcessingBase.TypeRecorder.WAV
    }

    private fun stopRecorder() {
        if (recorder2 == null) return
        if (recorder2!!.isRecorded()) {
            recorder2!!.stop()
        }
    }


}