package com.mobile.adain;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class StyleViewHolder extends RecyclerView.ViewHolder {
    public TextView titleTV;
    public ImageView styleIV;
    public ImageView applyIV;

    public StyleViewHolder(@NonNull View itemView) {
        super(itemView);

        titleTV = itemView.findViewById(R.id.titleTV);
        styleIV = itemView.findViewById(R.id.styleIV);
        applyIV = itemView.findViewById(R.id.applyIV);
    }
}
