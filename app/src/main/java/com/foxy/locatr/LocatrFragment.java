package com.foxy.locatr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;

public class LocatrFragment extends SupportMapFragment {
    private static final String TAG = "LocatrFragment";
    private static final String[] LOCATION_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private static final int REQUEST_LOCATION_PERMISSION = 0;

    private GoogleApiClient client;
    private GoogleMap map;

    private Bitmap mapImage;
    private GalleryItem mapItem;
    private Location currentLocation;

    public static LocatrFragment newInstance() {
        return new LocatrFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);          // подключение меню

        client = new GoogleApiClient.Builder(getActivity())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        // перерисовка панели инструментов при подключении
                        getActivity().invalidateOptionsMenu();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                    }
                })
                .build();

        // асинхронно получает объект карты
        getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                map = googleMap;
                updateUi();
            }
        });
    }


    @Override
    public void onStart() {
        super.onStart();
        getActivity().invalidateOptionsMenu();
        client.connect();
    }

    @Override
    public void onStop() {
        super.onStop();

        client.disconnect();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_locatr, menu);

        // активация кнопки поиска, если клиент подключен
        MenuItem searchItem = menu.findItem(R.id.action_locate);
        searchItem.setEnabled(client.isConnected());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_locate:
                if (hasLocationPermission()) {
                    findImage();
                } else {
                    requestPermissions(LOCATION_PERMISSIONS, REQUEST_LOCATION_PERMISSION); // запрос на предоставление разрешений
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Вызывается после нажатия пользователем кнопок в диалоговом окне о предоставлении разрешений
    // Повторно проверяет предоставлено ли разрешение
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                if (hasLocationPermission()) {
                    findImage();
                }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }

    private void findImage() {
        // Запрос на получение позиционных данных
        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);  // выбор между расходом заряда и точностью запроса
        request.setNumUpdates(1);                                     // сколько раз должны обновляться данные
        request.setInterval(0);                                       // частота обновления данных, 0 = как можно быстрее

        // Отправка запроса и получение результата
        LocationServices.FusedLocationApi
                .requestLocationUpdates(client, request, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        Log.i(TAG, "Got a fix: " + location);
                        new SearchTask().execute(location);
                    }
                });
    }

    // Проверка наличия разрешений
    private boolean hasLocationPermission() {
        int result = ContextCompat
                .checkSelfPermission(getActivity(), LOCATION_PERMISSIONS[0]);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    // масштабирование
    private void updateUi() {
        if (map == null || mapImage == null) {
            return;
        }

        // получение своих координат и координат картинки
        LatLng itemPoint = new LatLng(mapItem.getLat(), mapItem.getLon());
        LatLng myPoint = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

        // создание маркера для картинки
        BitmapDescriptor itemBitmap = BitmapDescriptorFactory.fromBitmap(mapImage);
        MarkerOptions itemMarker = new MarkerOptions()
                .position(itemPoint)
                .icon(itemBitmap);
        // создание своего макера
        MarkerOptions myMarker = new MarkerOptions()
                .position(myPoint);

        // удаляем лишнее, устанавливаем созданные маркеры
        map.clear();
        map.addMarker(itemMarker);
        map.addMarker(myMarker);

        // объект, на который будет наводиться камера
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(itemPoint)
                .include(myPoint)
                .build();

        // наведение камеры на объект bounds
        int margin = getResources().getDimensionPixelSize(R.dimen.map_inset_margin);
        CameraUpdate update = CameraUpdateFactory.newLatLngBounds(bounds, margin);
        map.animateCamera(update); // обновление карты
    }

    // AsyncTask
    // Поиск фото, установка
    private class SearchTask extends AsyncTask<Location, Integer, Void> {
        private GalleryItem galleryItem;
        private Bitmap bitmap;
        private Location location;

        @Override
        protected Void doInBackground(Location... locations) {
            location = locations[0];
            FlickrFetchr fetchr = new FlickrFetchr();
            List<GalleryItem> items = fetchr.searchPhotos(locations[0]);

            if (items.size() == 0) {
                return null;
            }

            galleryItem = items.get(0);

            try {
                byte[] bytes = fetchr.getUrlBytes(galleryItem.getUrl());
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (IOException e) {
                Log.i(TAG, "Unable to download bitmap", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mapImage = bitmap;
            mapItem = galleryItem;
            currentLocation = location;

            updateUi();
        }
    }
}
