package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.smsmessenger.R

class SearchActivity : SimpleActivity() {

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        updateMenuItemColors(menu)
        return true
    }
}
