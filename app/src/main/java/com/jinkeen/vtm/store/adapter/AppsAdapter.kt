package com.jinkeen.vtm.store.adapter

import android.graphics.Color
import coil.load
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseDataBindingHolder
import com.jinkeen.base.util.string
import com.jinkeen.vtm.store.R
import com.jinkeen.vtm.store.databinding.ActivityHomeItemBinding
import com.jinkeen.vtm.store.entity.AppInfoEntity

class AppsAdapter : BaseQuickAdapter<AppInfoEntity, BaseDataBindingHolder<ActivityHomeItemBinding>>(R.layout.activity_home_item) {

    override fun convert(holder: BaseDataBindingHolder<ActivityHomeItemBinding>, item: AppInfoEntity) {
        holder.dataBinding?.let { binding ->
            binding.logo.load(item.logo)
            binding.name.text = item.name
            binding.verName.text = R.string.fm_label_last_ver.string(item.verName)
            binding.root.setBackgroundColor(if (item.isChecked) Color.WHITE else Color.TRANSPARENT)
        }
    }
}