package tv.own.owntv.di

import okhttp3.OkHttpClient
import org.koin.dsl.module
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.features.setup.M3uKodiPropsInterceptor

/**
 * The app's single override of a core binding.
 *
 * [M3uKodiPropsInterceptor] has to sit on the client that downloads playlists, and that client
 * is created inside `core`'s data module; the parser it feeds is final and cannot be replaced. So
 * this module re-registers `HttpClient` with the same OkHttp client plus that one interceptor and
 * is loaded last (see `OwnTVApp`). Everything else — proxy, DNS, timeouts, the default User-Agent —
 * is inherited from the original client through `newBuilder()`.
 *
 * Only `HttpClient` is affected: the player's `StreamingHttpClient` keeps its own connection pool.
 */
val playlistModule = module {
    single {
        HttpClient(
            get<OkHttpClient>().newBuilder()
                .addInterceptor(M3uKodiPropsInterceptor())
                .build(),
        )
    }
}
