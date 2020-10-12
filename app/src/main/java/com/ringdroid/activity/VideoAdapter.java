package com.ringdroid.activity;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.ringdroid.testvideoedit.R;
import com.ringdroid.util.TimeUtil;

import java.util.List;


public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoItemViewHolder> {
    private List<Video> mVideoList;

    private Context mContext;

    private OnItemClickListener mOnItemClickListener;

    public VideoAdapter(Context context) {
        mContext = context;
    }

    @NonNull
    @Override
    public VideoItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new VideoItemViewHolder(LayoutInflater.from(mContext).inflate(R.layout.video_item, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VideoItemViewHolder holder, final int position) {
        final Video video = mVideoList.get(position);
        holder.timeView.setText(TimeUtil.format(video.getDuration()));
        Glide.with(mContext).load(video.getVideoPath()).into(holder.imageView);
        holder.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mOnItemClickListener != null) {
                    mOnItemClickListener.onItemClick(position, video);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mVideoList == null ? 0 : mVideoList.size();
    }

    public void setVideoList(List<Video> videoList) {
        mVideoList = videoList;
        notifyDataSetChanged();
    }

    static class VideoItemViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView timeView;

        VideoItemViewHolder(@NonNull View itemView) {
            super(itemView);

            imageView = itemView.findViewById(R.id.iv);
            timeView = itemView.findViewById(R.id.tv_duration);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    public interface OnItemClickListener  {
        void onItemClick(int position, Video video);
    }

}
