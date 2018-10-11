package com.aykuttasil.callrecord.receiver

import an.xuan.tong.historycontact.Constant
import an.xuan.tong.historycontact.api.ApiService
import an.xuan.tong.historycontact.api.Repository
import an.xuan.tong.historycontact.api.model.InformationResponse
import an.xuan.tong.historycontact.api.model.Message
import an.xuan.tong.historycontact.call.CallRecord
import an.xuan.tong.historycontact.call.helper.PrefsHelper
import an.xuan.tong.historycontact.call.receiver.PhoneCallReceiver
import an.xuan.tong.historycontact.realm.ApiCaching
import an.xuan.tong.historycontact.realm.HistoryContactConfiguration
import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.Schedulers.*
import io.realm.Realm
import retrofit2.http.GET
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.collections.HashMap
import kotlin.collections.hashMapOf

/**
 * Created by aykutasil on 19.10.2016.
 */
class CallRecordReceiver : PhoneCallReceiver {

    protected lateinit var callRecord: CallRecord
    private var audiofile: File? = null
    private var isRecordStarted = false

    constructor() {}

    constructor(callRecord: CallRecord) {
        this.callRecord = callRecord
    }

    override fun onIncomingCallReceived(context: Context, number: String, start: Date) {

    }

    override fun onIncomingCallAnswered(context: Context, number: String, start: Date) {
        Log.e("antx", "call onIncomingCallAnswered")
        startRecord(context, "incoming", number)
    }

    override fun onIncomingCallEnded(context: Context, number: String, start: Date, end: Date) {
        Log.e("antx", "call onIncomingCallEnded")
        stopRecord(context)
    }

    override fun onOutgoingCallStarted(context: Context, number: String, start: Date) {
        startRecord(context, "outgoing", number)
        Log.e("antx", "call onOutgoingCallStarted")
    }

    override fun onOutgoingCallEnded(context: Context, number: String, start: Date, end: Date) {
        stopRecord(context)
        Log.e("antx", "call onOutgoingCallEnded")
    }

    override fun onMissedCall(context: Context, number: String, start: Date) {
        Log.e("antx", "call onMissedCall")
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

    private fun stopRecord(context: Context) {
        try {
            if (recorder != null && isRecordStarted) {
                releaseMediaRecorder()
                isRecordStarted = false
                onRecordingFinished(context, callRecord, audiofile)
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
            fileNameBuilder.append("_")

            if (show_seed) {
                fileNameBuilder.append(seed)
                fileNameBuilder.append("_")
            }

            if (show_phone_number) {
                fileNameBuilder.append(phoneNumber)
                fileNameBuilder.append("_")
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
                    suffix = ".amr"
                }
            }

            audiofile = File.createTempFile(file_name, suffix, sampleDir)

            recorder = MediaRecorder()
            recorder!!.setAudioSource(audio_source)
            recorder!!.setOutputFormat(output_format)
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

    private fun insertCall(acountId: Int, phoneNunber: String, datecreate: String, contentmessage: String, type: String) {
        var status = (type == "SENT")
        val token = convertJsonToObject(getCacheInformation()?.data).token
        var hmAuthToken = hashMapOf("Authorization" to "Bearer$token")
        var id = convertJsonToObject(getCacheInformation()?.data).data?.id
        val mAuthToken = HashMap(hmAuthToken)
        var account = Gson().toJson(convertJsonToObject(getCacheInformation()?.data).data)
        var message = Message(id, acountId, phoneNunber,
                datecreate, "location", contentmessage, status, account)
        Log.e("dataSend", " " + message.toString())
        acountId?.let {
            Repository.createService(ApiService::class.java, mAuthToken).insertMessage(message, Constant.KEY_API)
                    .subscribeOn(io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            { result ->
                                Log.e("antx", "insertSms" + result.toString())

                            },
                            { e ->
                                Log.e("test", "insertSms error" + e.message)
                            })
        }
    }

    private fun convertJsonToObject(json: String?): InformationResponse {
        return Gson().fromJson(json, object : TypeToken<InformationResponse?>() {}.type)
    }

    private fun getCacheInformation(): ApiCaching? {
        val mRealm = Realm.getInstance(HistoryContactConfiguration.createBuilder().build())
        val mangaSearchObj: ApiCaching? = mRealm.where(ApiCaching::class.java).contains("apiName", mKeyAPI).findFirst()
        // clone data if don't have this line -> crash app after "mRealm.close()"
        val result = ApiCaching(mangaSearchObj?.apiName, mangaSearchObj?.data, mangaSearchObj?.updateAt)
        mRealm.close()
        return result
    }

    private val mKeyAPI: String by lazy {
        // Get Value of annotation API for save cache as KEY_CACHE
        val method = ApiService::getInfomation
        val get = method.annotations.find { it is GET } as? GET
        get?.value + ""
    }

}
