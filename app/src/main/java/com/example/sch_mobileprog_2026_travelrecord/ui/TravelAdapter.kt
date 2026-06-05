package com.example.sch_mobileprog_2026_travelrecord.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.example.sch_mobileprog_2026_travelrecord.R
import com.example.sch_mobileprog_2026_travelrecord.data.TravelRecord
import com.example.sch_mobileprog_2026_travelrecord.databinding.ItemTravelRecordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 리사이클러뷰(RecyclerView) 연동을 위한 어댑터 클래스.
 * 코루틴 비동기 썸네일 디코딩 최적화(RGB_565, inSampleSize)를 수동 적용하여 메모리 오버헤드(OOM)를 원천 차단함.
 */
class TravelAdapter(
    private val lifecycleScope: LifecycleCoroutineScope, // 이미지 비동기 로딩을 위한 코루틴 스코프 주입
    private val onItemClick: (Int) -> Unit               // 항목 클릭 시 고유 ID(no)를 반환하는 콜백 람다
) : RecyclerView.Adapter<TravelAdapter.TravelViewHolder>() {

    private var items = listOf<TravelRecord>()

    /**
     * 리스트 데이터를 갱신함.
     */
    fun submitList(newItems: List<TravelRecord>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TravelViewHolder {
        val binding = ItemTravelRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TravelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TravelViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /**
     * 개별 항목의 뷰를 제어하고 바인딩하는 ViewHolder 내부 클래스.
     */
    inner class TravelViewHolder(private val binding: ItemTravelRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TravelRecord) {
            binding.textPlace.text = item.place
            binding.textVisitDate.text = item.visitDate

            // 클릭 이벤트 처리: item.no가 null이 아닐 때 콜백 수행
            binding.root.setOnClickListener {
                item.no?.let { no -> onItemClick(no) }
            }

            val context = binding.root.context

            // 이미지 로딩 전 기본 placeholder 이미지로 즉각 초기화 (뷰 재활용 오작동 방지)
            binding.imageThumbnail.setImageResource(R.drawable.default_image)

            // 원본 파일 경로가 있을 경우 코루틴 백그라운드 스레드에서 저수준 디코딩 및 다운샘플링 비동기 실행
            if (!item.photoUri.isNullOrEmpty()) {
                lifecycleScope.launch {
                    val bitmap = loadThumbnail(context, item.photoUri)
                    if (bitmap != null) {
                        binding.imageThumbnail.setImageBitmap(bitmap)
                    } else {
                        binding.imageThumbnail.setImageResource(R.drawable.default_image)
                    }
                }
            }
        }
    }

    /**
     * OOM 방지 및 성능 보장을 위한 저수준 썸네일 디코딩 파이프라인.
     * inJustDecodeBounds로 해상도만 선 분석하고, targetSize에 맞춘 배수로 다운샘플링(inSampleSize)함.
     * 투명도 처리가 불필요하므로 RGB_565를 강제하여 메모리 사용량을 50% 추가 절감함.
     */
    private suspend fun loadThumbnail(context: Context, uriString: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true // 비트맵 데이터를 로드하지 않고 해상도 크기만 조회
                }

                // 1단계: 원본 해상도 파싱
                context.contentResolver.openInputStream(uri).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                // 2단계: 최적화 배수 계산 (타겟 썸네일 사이즈: 150px내외 타겟팅)
                val targetSize = 160
                options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565 // 픽셀당 2바이트 포맷 강제

                // 3단계: 다운샘플링된 비트맵 힙 메모리에 적재
                context.contentResolver.openInputStream(uri).use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null // 로딩 실패 시 null 반환 (default_image로 복구)
            }
        }

    /**
     * 원본 이미지 크기와 타겟 썸네일 픽셀 크기를 비교하여 2의 거듭제곱 배수(inSampleSize)를 계산함.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
