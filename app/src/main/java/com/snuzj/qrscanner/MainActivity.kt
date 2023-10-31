package com.snuzj.qrscanner

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.snuzj.qrscanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object{
        private const val CAMERA_REQUEST_CODE = 100
        private const val GALLERY_REQUEST_CODE = 101

    }

    private lateinit var cameraPermissions: Array<String>
    private lateinit var galleryPermission: Array<String>

    private val cameraResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // The image was captured successfully.
                binding.imageIv.setImageURI(imageUri)
            } else {
                showToast("Camera capture canceled.")
            }
        }

    private var imageUri: Uri? = null
    private var barcodeScannerOptions: BarcodeScannerOptions? = null
    private var barcodeScanner: BarcodeScanner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        galleryPermission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        barcodeScannerOptions = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()

        barcodeScanner = BarcodeScanning.getClient(barcodeScannerOptions!!)


        // Handle the camera button click.
        binding.cameraBtn.setOnClickListener {
            if (checkCameraPermission()) {
                pickImageCamera()
            } else {
                // Request camera permission from the user.
                requestCameraPermission()
            }
        }

        binding.galleryBtn.setOnClickListener {
            if (checkStoragePermission())
                pickImageGallery()
            else
                requestStoragePermission()
        }

        binding.scanBtn.setOnClickListener {
            if (imageUri == null){
                showToast("Pick Image First")
            }
            else{
                detectResultFromImage()
            }
        }
    }

    private fun detectResultFromImage() {
        try {
            val inputImage = InputImage.fromFilePath(this, imageUri!!)
            val barcodeResult = barcodeScanner!!.process(inputImage)
                .addOnSuccessListener {barcodes->
                    extractBarcodeQRcodeInfo(barcodes)
                }
                .addOnFailureListener{e->
                    showToast("Failure scanning due to ${e.message}")
                }
        } catch (e: Exception){

        }
    }

    private fun extractBarcodeQRcodeInfo(barcodes: List<Barcode>) {
        for (barcode in barcodes){
            val bound = barcode.boundingBox
            val corners = barcode.cornerPoints
            val rawValue = barcode.rawValue

            when(barcode.valueType){
                Barcode.TYPE_WIFI->{
                    val typeWifi = barcode.wifi
                    val ssid = "${typeWifi?.ssid}"
                    val password = "${typeWifi?.password}"
                    var encryptionType = "${typeWifi?.encryptionType}"

                    when (encryptionType) {
                        "1" -> encryptionType = "OPEN"
                        "2" -> encryptionType = "WPA"
                        "3" -> encryptionType = "WEP"
                    }

                    binding.resultTv.text = "TYPE_WIFI \nssid: $ssid \npassword: $password \nencryptionType: $encryptionType \n \n$rawValue"
                }
                Barcode.TYPE_URL->{
                    val typeUrl = barcode.url
                    val title = "${typeUrl?.title}"
                    val url = "${typeUrl?.url}"

                    binding.resultTv.text = "TYPE_URL \ntitle: $title \nurl: $url \n\n$rawValue"
                }
                Barcode.TYPE_EMAIL->{
                    val typeEmail = barcode.email
                    val address = "${typeEmail?.address}"
                    val body = "${typeEmail?.body}"
                    val subject = "${typeEmail?.subject}"

                    binding.resultTv.text = "TYPE_EMAIL \naddress: $address \nbody: $body \nsubject: $subject \n\n$rawValue"
                }
                Barcode.TYPE_CONTACT_INFO->{
                    val typeContactInfo = barcode.contactInfo
                    val name = "${typeContactInfo?.name?.formattedName}"
                    val organization = "${typeContactInfo?.organization}"
                    val title = "${typeContactInfo?.title}"

                    var phones = ""
                    typeContactInfo?.phones?.forEach { phone ->
                        phones += "\n${phone.number}"
                    }

                    var emails = ""
                    typeContactInfo?.emails?.forEach { email ->
                        emails += "\n${email.address}"
                    }

                    binding.resultTv.text = "TYPE_CONTACT_INFO \nname: $name \norganization: $organization \ntitle: $title \nphones: $phones \nemails: $emails \n\n$rawValue"
                }
                else->{
                    binding.resultTv.text = "rawValue: $rawValue"
                }
            }
        }
    }

    private fun pickImageGallery(){
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)

    }

    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ){result->

        if (result.resultCode == Activity.RESULT_OK){

            val data = result.data

            imageUri = data?.data

            binding.imageIv.setImageURI(imageUri)
        }
        else{
            showToast("Cancelled.")
        }
    }

    private fun checkStoragePermission(): Boolean{

        val result = (ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        return result;
    }

    private fun requestStoragePermission(){
        ActivityCompat.requestPermissions(this,galleryPermission, GALLERY_REQUEST_CODE)
    }

    private fun pickImageCamera() {
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.Media.TITLE, "Image")
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, "Image Description")

        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

        // Start the camera activity using the camera result handler.
        cameraResult.launch(intent)
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (checkStoragePermission()) {
                        pickImageCamera()
                    } else {
                        requestStoragePermission()
                    }
                } else {
                    showToast("Camera and Storage permissions are required")
                }
            }
            GALLERY_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickImageGallery()
                } else {
                    showToast("Storage permission is required")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    }
}