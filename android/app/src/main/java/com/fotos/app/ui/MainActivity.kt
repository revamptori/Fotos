package com.fotos.app.ui

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fotos.app.R
import com.fotos.app.data.MediaStoreSync
import com.fotos.app.databinding.ActivityMainBinding
import com.fotos.app.ui.albums.AlbumsFragment
import com.fotos.app.ui.photos.PhotosFragment
import com.fotos.app.ui.search.SearchFragment
import com.fotos.app.ui.settings.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            binding.permissionBanner.visibility = View.GONE
            syncAndRefresh()
        } else {
            binding.permissionBanner.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermission.setOnClickListener {
            permissionLauncher.launch(MediaStoreSync.requiredPermissions())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_photos -> showFragment(PhotosFragment())
                R.id.nav_albums -> showFragment(AlbumsFragment())
                R.id.nav_search -> showFragment(SearchFragment())
                R.id.nav_settings -> showFragment(SettingsFragment())
                else -> false
            }
        }

        if (savedInstanceState == null) {
            showFragment(PhotosFragment())
        }

        checkPermissionsAndSync()
    }

    private fun checkPermissionsAndSync() {
        if (MediaStoreSync.hasMediaPermissions(this)) {
            binding.permissionBanner.visibility = View.GONE
            syncAndRefresh()
        } else {
            binding.permissionBanner.visibility = View.VISIBLE
            permissionLauncher.launch(MediaStoreSync.requiredPermissions())
        }
    }

    private fun syncAndRefresh() {
        lifecycleScope.launch {
            MediaStoreSync.syncAll(this@MainActivity)
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current is Refreshable) {
                current.refresh()
            }
        }
    }

    private fun showFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        return true
    }

    interface Refreshable {
        fun refresh()
    }
}
