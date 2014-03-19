package org.kontalk.ui;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;

public class MyMapFragment extends SupportMapFragment {

    @Override
    public void onStart() {
        super.onStart();
        GoogleMap map = this.getMap();
        map.setMyLocationEnabled(true);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
    }

    @Override
    public void onStop() {
        super.onStop();
        FragmentParent p = (FragmentParent) getActivity();
        p.onChildClose(this);
    }
}
