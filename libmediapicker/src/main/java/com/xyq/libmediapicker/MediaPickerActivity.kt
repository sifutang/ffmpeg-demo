package com.xyq.libmediapicker

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.loader.app.LoaderManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xyq.libmediapicker.adapter.FolderAdapter
import com.xyq.libmediapicker.adapter.MediaGridAdapter
import com.xyq.libmediapicker.adapter.SpacingDecoration
import com.xyq.libmediapicker.data.ImageLoader
import com.xyq.libmediapicker.data.MediaDataCallback
import com.xyq.libmediapicker.data.MediaLoader
import com.xyq.libmediapicker.data.VideoLoader
import com.xyq.libmediapicker.entity.Folder
import com.xyq.libmediapicker.entity.Media
import com.xyq.libmediapicker.utils.ScreenUtils
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions

class MediaPickerActivity: FragmentActivity(), MediaDataCallback, View.OnClickListener  {

    private lateinit var argsIntent: Intent
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnDone: Button
    private lateinit var btnCategory: Button
    private lateinit var folderPopupWindow: ListPopupWindow
    private lateinit var gridAdapter: MediaGridAdapter
    private lateinit var folderAdapter: FolderAdapter

    private var onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            doSelectMediaFileFinish(ArrayList())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        argsIntent = intent

        setContentView(R.layout.main_media_picker)
        recyclerView = findViewById(R.id.recycler_view)
        btnDone = findViewById(R.id.btn_done)
        btnDone.setOnClickListener(this)
        btnCategory = findViewById(R.id.btn_category)
        btnCategory.setOnClickListener(this)
        findViewById<ImageView>(R.id.btn_back).setOnClickListener(this)

        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        setTitleBar()
        createAdapter()
        createFolderAdapter()
        loadMedaData()
    }

    override fun onDestroy() {
        onBackPressedCallback.remove()
        Glide.get(this).clearMemory()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    private fun setTitleBar() {
        when (argsIntent.getIntExtra(PickerConfig.SELECT_MODE, PickerConfig.PICKER_IMAGE_VIDEO)) {
            PickerConfig.PICKER_IMAGE_VIDEO -> {
                (findViewById<View>(R.id.bar_title) as TextView).text = getString(R.string.select_title)
            }
            PickerConfig.PICKER_IMAGE -> {
                (findViewById<View>(R.id.bar_title) as TextView).text = getString(R.string.select_image_title)
            }
            PickerConfig.PICKER_VIDEO -> {
                (findViewById<View>(R.id.bar_title) as TextView).text = getString(R.string.select_video_title)
            }
        }
    }

    private fun createAdapter() {
        val layoutManager = GridLayoutManager(this, PickerConfig.GridSpanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(SpacingDecoration(PickerConfig.GridSpanCount, PickerConfig.GridSpace))
        recyclerView.setHasFixedSize(true)

        val medias = ArrayList<Media>()
        val select = argsIntent.getParcelableArrayListExtra<Media>(PickerConfig.DEFAULT_SELECTED_LIST)
        val maxSelectCount = argsIntent.getIntExtra(PickerConfig.MAX_SELECT_COUNT, PickerConfig.DEFAULT_SELECTED_MAX_COUNT)
        gridAdapter = MediaGridAdapter(this, medias, select, maxSelectCount)
        recyclerView.adapter = gridAdapter
    }

    private fun createFolderAdapter() {
        val folders = ArrayList<Folder>()
        folderAdapter = FolderAdapter(folders, this)

        val h = ScreenUtils.getScreenHeight(this) * 0.6
        folderPopupWindow = ListPopupWindow(this).apply {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setAdapter(folderAdapter)
            height = h.toInt()
            anchorView = findViewById(R.id.footer)
            isModal = true
        }
        folderPopupWindow.setOnItemClickListener { _, _, position, _ ->
            folderAdapter.setSelectIndex(position)
            btnCategory.text = folderAdapter.getItem(position).name
            gridAdapter.updateAdapter(folderAdapter.getSelectMedias())
            folderPopupWindow.dismiss()
        }
    }

    @AfterPermissionGranted(119)
    private fun loadMedaData() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            when (val type = argsIntent.getIntExtra(PickerConfig.SELECT_MODE, PickerConfig.PICKER_VIDEO)) {
                PickerConfig.PICKER_IMAGE_VIDEO -> {
                    LoaderManager.getInstance(this).initLoader(type, null, MediaLoader(this, this))
                }
                PickerConfig.PICKER_IMAGE -> {
                    LoaderManager.getInstance(this).initLoader(type, null, ImageLoader(this, this))
                }
                PickerConfig.PICKER_VIDEO -> {
                    LoaderManager.getInstance(this).initLoader(type, null, VideoLoader(this, this))
                }
            }
        } else {
            EasyPermissions.requestPermissions(this, getString(R.string.READ_EXTERNAL_STORAGE), 119, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onMediaDataArrived(list: ArrayList<Folder>) {
        if (list.isEmpty()) return
        gridAdapter.updateAdapter(list[0].getMedias())
        refreshDoneBtnText()
        gridAdapter.setOnItemClickListener(object : MediaGridAdapter.OnRecyclerViewItemClickListener {
            override fun onItemClick(view: View?, data: Media?, selectMedias: ArrayList<Media>?) {
                refreshDoneBtnText()
            }
        })
        btnCategory.text = list[0].name
        folderAdapter.updateAdapter(list)
    }

    override fun onClick(v: View?) {
        if (v?.id == R.id.btn_back) {
            doSelectMediaFileFinish(ArrayList())
        } else if (v?.id == R.id.btn_category) {
            if (folderPopupWindow.isShowing) {
                folderPopupWindow.dismiss()
            } else {
                folderPopupWindow.show()
            }
        } else if (v?.id == R.id.btn_done) {
            doSelectMediaFileFinish(gridAdapter.getSelectMedias())
        }
    }

    private fun refreshDoneBtnText() {
        val max = argsIntent.getIntExtra(PickerConfig.MAX_SELECT_COUNT, PickerConfig.DEFAULT_SELECTED_MAX_COUNT)
        val select = gridAdapter.getSelectMedias()
        select?.let {
            btnDone.text = "${getString(R.string.done)}(${it.size}/$max)"
        }
    }

    private fun doSelectMediaFileFinish(selects: ArrayList<Media>?) {
        val intent = Intent().apply {
            putParcelableArrayListExtra(PickerConfig.EXTRA_RESULT, selects)
        }
        setResult(PickerConfig.RESULT_CODE, intent)
        finish()
    }
}