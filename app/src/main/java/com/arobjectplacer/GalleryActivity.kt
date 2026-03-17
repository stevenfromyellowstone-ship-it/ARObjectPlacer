package com.arobjectplacer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arobjectplacer.databinding.ActivityGalleryBinding
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var objectStore: ObjectStore
    private lateinit var adapter: ObjectAdapter
    private var isARMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        objectStore = (application as ARObjectPlacerApp).objectStore
        isARMode = intent.getStringExtra("mode") == "ar_placement"
        binding.tvTitle.text = if (isARMode) "Select Object to Place" else "Captured Objects"
        binding.btnBack.setOnClickListener { finish() }
        adapter = ObjectAdapter(
            onClick = { obj -> if (isARMode) startActivity(Intent(this, ARPlacementActivity::class.java).putExtra("object_id", obj.id)) },
            onLongClick = { obj ->
                AlertDialog.Builder(this).setTitle("Delete?").setMessage("Delete '${obj.name}'?")
                    .setPositiveButton("Delete") { _, _ -> lifecycleScope.launch { objectStore.deleteObject(obj) } }
                    .setNegativeButton("Cancel", null).show()
            })
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter
        lifecycleScope.launch {
            objectStore.getAllObjects().collectLatest {
                adapter.submitList(it)
                binding.tvEmpty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

class ObjectAdapter(
    private val onClick: (CapturedObject) -> Unit,
    private val onLongClick: (CapturedObject) -> Unit
) : RecyclerView.Adapter<ObjectAdapter.VH>() {
    private var items: List<CapturedObject> = emptyList()
    fun submitList(list: List<CapturedObject>) { items = list; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.ivObjectThumbnail)
        val name: TextView = v.findViewById(R.id.tvObjectName)
        val dims: TextView = v.findViewById(R.id.tvObjectDimensions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_captured_object, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val obj = items[position]
        holder.name.text = obj.name
        holder.dims.text = String.format("%.0f × %.0f × %.0f cm", obj.widthMeters*100, obj.heightMeters*100, obj.depthMeters*100)
        Glide.with(holder.img.context).load(obj.thumbnailPath ?: obj.imagePath).centerCrop().into(holder.img)
        holder.itemView.setOnClickListener { onClick(obj) }
        holder.itemView.setOnLongClickListener { onLongClick(obj); true }
    }

    override fun getItemCount() = items.size
}
