import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
    .build()

private val BASE_URL = "http://127.0.0.1:9999"
private val gson = Gson()

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(this::newCall).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

            })
    }
}

private suspend fun <T> makeRequest(url: String, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        gson.fromJson(client.apiCall(url).body?.string(), typeToken)
    }

suspend fun getPost(): List<Post> {
    return makeRequest(url = "$BASE_URL/api/slow/posts/", object : TypeToken<List<Post>>() {})
}

suspend fun getComment(idPost: Long): List<Comment> {
    return makeRequest(url = "$BASE_URL/api/slow/posts/$idPost/comments/", object : TypeToken<List<Comment>>() {})
}

suspend fun getAuthor(idAuthor: Long): Author {
    return makeRequest(url = "$BASE_URL/api/authors/$idAuthor", object : TypeToken<Author>() {})
}

fun main() {
    CoroutineScope(EmptyCoroutineContext)
        .launch {
            try {
                val posts = getPost()
                    .map { post ->
                        async {
                            val author = getAuthor(post.authorId)
                            val comment = getComment(post.id)
                                .map { comment ->
                                    async { CommentAuthor(comment, getAuthor(author.id)  ) }

                                }.awaitAll()
                            PostsAuthor(post, author, comment)
                        }
                    }.awaitAll()
                println(posts)
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    Thread.sleep(100000)
}

