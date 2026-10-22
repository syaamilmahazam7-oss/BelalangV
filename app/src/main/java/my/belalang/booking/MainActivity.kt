package my.belalang.booking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import my.belalang.booking.ui.BookingApp
import my.belalang.booking.ui.BookingViewModel

class MainActivity : ComponentActivity() {
    private val vm: BookingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Deeplink pilihan: ?jobId=...&jobType=... (skema belum ditetapkan; lihat README).
        val data = intent?.data
        val jobId = data?.getQueryParameter("jobId")
        if (savedInstanceState == null && !jobId.isNullOrBlank()) {
            vm.openFromDeepLink(jobId, data.getQueryParameter("jobType"))
        }
        setContent {
            MaterialTheme { BookingApp(vm) }
        }
    }
}
