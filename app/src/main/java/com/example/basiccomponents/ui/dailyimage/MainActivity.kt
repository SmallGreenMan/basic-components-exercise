package com.example.basiccomponents.ui.dailyimage

import android.app.DatePickerDialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.basiccomponents.R
import com.example.basiccomponents.databinding.ActivityMainBinding
import com.example.basiccomponents.extensions.alphaFade
import com.example.basiccomponents.network.repo.NasaRepository
import com.example.basiccomponents.ui.models.NasaDailyImage
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.util.*

class MainActivity : AppCompatActivity() {

    /**
     * Don't touch these 3 fields
     */
    private val nasaRepository by inject<NasaRepository>()
    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable -> handleError(throwable) }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
    }

    private fun initViews(data: NasaDailyImage) {
        showNetworkError(false)

        with(binding) {
            toolbar.subtitle = data.title

            Glide.with(ivNasaImage)
                .load(data.imageUrl)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        showLoading(false)
                        return false
                    }
                })
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(ivNasaImage)

            tvDate.text = data.date
            tvDescription.text = data.explanation
        }
    }

    private fun showNetworkError(showError: Boolean) {
        with(binding.avNetworkLost) {
            if (isVisible != showError) {
                if (showError) {
                    isVisible = showError
                    playAnimation()
                }
                alphaFade(showError)
                if (!showError) {
                    cancelAnimation()
                    isVisible = showError
                }
            }
        }
    }

    private fun showLoading(showLoading: Boolean) {
        with(binding) {
            if (showLoading) {
                contentLayout.alphaFade(!showLoading)
                avLoading.apply {
                    alphaFade(showLoading)
                    playAnimation()
                }
            } else {
                avLoading.apply {
                    alphaFade(showLoading)
                    cancelAnimation()
                }
                contentLayout.alphaFade(!showLoading)
            }
        }

    }

    private fun pickDate() {
        val currentDateTime = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val newDate = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                fetchDailyImage(Date(newDate.timeInMillis))
            },
            currentDateTime.get(Calendar.YEAR),
            currentDateTime.get(Calendar.MONTH),
            currentDateTime.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    private fun handleError(throwable: Throwable) {
        Log.e(MainActivity::class.java.simpleName, "exception! CurrentThread: ${Thread.currentThread().name}", throwable)
        runOnUiThread {
            showLoading(false)
            showNetworkError(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.toolbar_menu, menu)
        val item = menu.findItem(R.id.theme_switch)
        item.setActionView(R.layout.switch_item)
        val mySwitch: SwitchCompat = item.actionView.findViewById(R.id.switchCompat)
        val isNightMode = AppCompatDelegate.MODE_NIGHT_YES == AppCompatDelegate.getDefaultNightMode()
        mySwitch.isChecked = isNightMode

        mySwitch.setOnCheckedChangeListener { _, _ ->
            changeTheme()
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_refresh -> {
                fetchDailyImage()
                return true
            }

            R.id.action_search -> {
                pickDate()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Don't edit this function. Use only for fetching data.
     */
    private fun fetchDailyImage(date: Date? = null) {
        showLoading(true)
        if (!coroutineScope.isActive) coroutineScope = CoroutineScope(Dispatchers.IO)

        coroutineScope.launch(exceptionHandler) {
            val result = nasaRepository.getDailyImage(date)
            Log.i(MainActivity::class.java.simpleName, "result: $result")

            withContext(Dispatchers.Main) {
                initViews(result)
            }
        }
    }

    /**
     * Changes the app theme to light or dark
     */
    private fun changeTheme() {
        var theme = AppCompatDelegate.getDefaultNightMode()
        theme = if (AppCompatDelegate.MODE_NIGHT_YES != theme)
            AppCompatDelegate.MODE_NIGHT_YES
        else
            AppCompatDelegate.MODE_NIGHT_NO

        AppCompatDelegate.setDefaultNightMode(theme)
    }

    override fun onStop() {
        super.onStop()
        coroutineScope.cancel()
    }
}