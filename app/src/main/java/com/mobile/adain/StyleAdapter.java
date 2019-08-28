package com.mobile.adain;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class StyleAdapter extends RecyclerView.Adapter<StyleViewHolder> {
    private ArrayList<Style> styleArrays;
    private RecyclerView styleListRC;
    private MainActivity instance;

    class StyleOnClickListener implements View.OnClickListener , View.OnLongClickListener{
        @Override
        public void onClick(View view) {
            if (instance.computing) return;
            int pos = styleListRC.getChildLayoutPosition(view);
            if (instance.mixMode) {
                if (styleArrays.get(pos).getIsApply() == 0)
                    instance.toggleStyle(pos);
                else
                    instance.changeStyleCursor(pos);
            } else {
                instance.toggleStyle(pos);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (instance.computing) return false;
            int pos = styleListRC.getChildLayoutPosition(view);
            if (instance.mixMode && styleArrays.get(pos).getIsApply() == 1)
                instance.toggleStyle(pos);
            return true;
        }
    }


    private final StyleOnClickListener onClickListener = new StyleOnClickListener();

    public StyleAdapter(ArrayList<Style> data, RecyclerView rc, MainActivity instance) {
        styleListRC = rc;
        styleArrays = data;
        this.instance = instance;
    }
    @NonNull
    @Override
    public StyleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycle_items, parent, false);
        view.setOnClickListener(onClickListener);
        view.setOnLongClickListener(onClickListener);
        return new StyleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StyleViewHolder holder, int position) {
        holder.titleTV.setText(styleArrays.get(position).getName());
        holder.styleIV.setImageResource(styleArrays.get(position).getStyleImageId());
        if (styleArrays.get(position).getIsApply() == 0) {
            holder.applyIV.setTag(R.drawable.ic_not_select_black_24dp);
            holder.applyIV.setImageResource(R.drawable.ic_not_select_black_24dp);
        }
        else {
            holder.applyIV.setTag(R.drawable.ic_selected_black_24dp);
            holder.applyIV.setImageResource(R.drawable.ic_selected_black_24dp);
        }
    }

    @Override
    public int getItemCount() {
        return styleArrays.size();
    }
}

