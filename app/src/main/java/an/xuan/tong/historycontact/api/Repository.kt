package an.xuan.tong.historycontact.api

import an.xuan.tong.historycontact.Constant
import android.provider.SyncStateContract
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import an.xuan.tong.historycontact.api.Repository.AuthenticationInterceptor
import android.text.TextUtils
import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.converter.gson.GsonConverterFactory


class Repository {
    companion object {
        private var retrofit: Retrofit? = null
        private var builder: Retrofit.Builder = Retrofit.Builder().baseUrl(Constant.URL_PATH)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())

        private var builder2: Retrofit.Builder = Retrofit.Builder().baseUrl(Constant.URL_PATH)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())

        private var builderGsonConverter: Retrofit.Builder = Retrofit.Builder().baseUrl(Constant.URL_PATH)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())

        private val httpClient = OkHttpClient.Builder()
        fun <S> createService(serviceClass: Class<S>): S {
            return createService(serviceClass, null)
        }

        fun <S> createService(serviceClass: Class<S>, authToken: Map<String, String>?): S {
            if (authToken != null) {
                var log = HttpLoggingInterceptor()
                log.setLevel(HttpLoggingInterceptor.Level.BODY);
                var interceptor = AuthenticationInterceptor(authToken!!)
                if (!httpClient.interceptors().contains(interceptor)) {
                    httpClient.addInterceptor(interceptor)
                    httpClient.addInterceptor(log)
                    builder.client(httpClient.build())
                    retrofit = builder.build()
                }
            }
            retrofit = builder.build()
            return retrofit!!.create(serviceClass)
        }
        fun <S> createService2(serviceClass: Class<S>, authToken: Map<String, String>?): S {
            if (authToken != null) {
                var log = HttpLoggingInterceptor()
                log.setLevel(HttpLoggingInterceptor.Level.BODY);
                var interceptor = AuthenticationInterceptor(authToken!!)
                if (!httpClient.interceptors().contains(interceptor)) {
                    httpClient.addInterceptor(interceptor)
                    httpClient.addInterceptor(log)
                    builder2.client(httpClient.build())
                    retrofit = builder2.build()
                }
            }
            retrofit = builder2.build()
            return retrofit!!.create(serviceClass)
        }

    }

    class AuthenticationInterceptor(private val authToken: Map<String, String>) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val builder = original.newBuilder()
            for (key in authToken.keys) {
                Log.e("auther", "key: " + key + " value: " + authToken.getValue(key))
                builder.header(key, authToken.getValue(key))
            }
            val request = builder.build()
            return chain.proceed(request)
        }
    }


}