package miercoles.dsl.bluetoothprintprueba;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.kosalgeek.android.photoutil.GalleryPhoto;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import miercoles.dsl.bluetoothprintprueba.listas.dispositivosbluetooth.DibujarActivity;
import miercoles.dsl.bluetoothprintprueba.utilidades.Constantes;
import miercoles.dsl.bluetoothprintprueba.utilidades.FotoDeCamara;
import miercoles.dsl.bluetoothprintprueba.utilidades.ManipuladorImagen;
import miercoles.dsl.bluetoothprintprueba.utilidades.PrintBitmap;
import miercoles.dsl.bluetoothprintprueba.utilidades.PrinterCommands;
import miercoles.dsl.bluetoothprintprueba.utilidades.Utils;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_DISPOSITIVO = 425;
    private static final int LIMITE_CARACTERES_POR_LINEA = 32;
    private static final String TAG_DEBUG = "tag_debug";
    private static final int IR_A_DIBUJAR = 632;
    private static final int COD_PERMISOS = 872;
    private static final int INTENT_CAMARA = 123;
    private static final int INTENT_GALERIA = 321;
    private final int ANCHO_IMG_58_MM = 384;
    private static final int MODE_PRINT_IMG = 0;
    /*Nuevas dimansiones, solo se usa IMAGE_SIZE para una imagen cuadrada*/
    public final static int WIDTH_PIXEL = 384;
    public final static int IMAGE_SIZE = 240;
    /**/
    private TextView txtLabel;
    private EditText edtTexto;
    private Button btnImprimirTexto, btnCerrarConexion;

    private Spinner spnFuente, spnNegrita, spnAncho, spnAlto;
    // Para la operaciones con dispositivos bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice dispositivoBluetooth;
    private BluetoothSocket bluetoothSocket;

    // identificador unico default
    private UUID aplicacionUUID = UUID
            .fromString("00001101-0000-1000-8000-00805F9B34FB");
    // Para el flujo de datos de entrada y salida del socket bluetooth
    private OutputStream outputStream;
    private InputStream inputStream;

    private Thread hiloComunicacion;
    // Para el manejo de la informacion en byte que fluye en los streams
    private byte[] bufferLectura;
    private int bufferLecturaPosicion;

    // volatile: no guarda una copia en chaché para cada hilo, si no que los sincroliza cuando cambien la variable
    // de esa manera todos manejaran el mismo valor de la variable y no una copia que puede estar con valor anterior
    private volatile boolean pararLectura;
    private ImageView imgDibujo;
    private Button btnDibujar;
    private Button btnImprimirImg;
    private String rutaImgDibujo, rutaFoto;
    private float gradosRotarImg = 0f;
    private EditText edtGradosRotar;
    private Button btnTomarFoto;
    private ImageView imgFoto;
    private FotoDeCamara cameraPhoto;
    private GalleryPhoto galleryPhoto;
    private Button btnImprimirFoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rutaImgDibujo = null;
        rutaFoto = null;

        txtLabel = (TextView) findViewById(R.id.txt_label);
        edtTexto = (EditText) findViewById(R.id.edt_texto);
        edtGradosRotar = (EditText) findViewById(R.id.edt_angulo_img);
        btnImprimirTexto = (Button) findViewById(R.id.btn_imprimir_texto);
        btnImprimirImg = (Button) findViewById(R.id.btn_imprimir_dibujo);
        spnNegrita = (Spinner) findViewById(R.id.spn_negrita);
        spnAlto = (Spinner) findViewById(R.id.spn_alto);
        spnFuente = (Spinner) findViewById(R.id.spn_fuente);
        spnAncho = (Spinner) findViewById(R.id.spn_ancho);
        imgDibujo = (ImageView) findViewById(R.id.img_dibujo);
        btnDibujar = (Button) findViewById(R.id.btn_dibujar);
        btnCerrarConexion = (Button) findViewById(R.id.btn_cerrar_conexion);
        imgFoto = (ImageView) findViewById(R.id.img_foto);
        btnTomarFoto = (Button) findViewById(R.id.btn_tomar_foto);
        btnImprimirFoto = (Button) findViewById(R.id.btn_imprimir_img);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        btnImprimirTexto.setOnClickListener(this);
        btnImprimirImg.setOnClickListener(this);
        btnCerrarConexion.setOnClickListener(this);
        btnDibujar.setOnClickListener(this);
        btnTomarFoto.setOnClickListener(this);
        btnImprimirFoto.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_imprimir_texto:
                if (bluetoothSocket != null) {
                    try {
                        String texto = edtTexto.getText().toString() + "\n";

                        int fuente = Integer.parseInt(spnFuente.getSelectedItem().toString());
                        int negrita = spnNegrita.getSelectedItem().toString().equals("Si") ? 1 : 0;
                        int ancho = Integer.parseInt(spnAncho.getSelectedItem().toString());
                        int alto = Integer.parseInt(spnAlto.getSelectedItem().toString());

                        // Para que acepte caracteres espciales
                        outputStream.write(0x1C); outputStream.write(0x2E); // Cancelamos el modo de caracteres chino (FS .)
                        outputStream.write(0x1B); outputStream.write(0x74); outputStream.write(0x10); // Seleccionamos los caracteres escape (ESC t n) - n = 16(0x10) para WPC1252

                        outputStream.write( getByteString(texto, negrita, fuente, ancho, alto) );

                        outputStream.write("\n\n".getBytes());

                    } catch (IOException e) {
                        Log.e(TAG_DEBUG, "Error al escribir en el socket");

                        Toast.makeText(this, "Error al interntar imprimir texto", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG_DEBUG, "Socket nulo");

                    txtLabel.setText("Impresora no conectada");
                }

                break;
            case R.id.btn_imprimir_dibujo:
                if (rutaImgDibujo != null) {
                    if (bluetoothSocket != null) {
                        try {
                            //Imagen del dibujo  a bitmap
                            Bitmap bitmap = BitmapFactory.decodeFile(rutaImgDibujo);

                            //Funciones para imprimir
                            printBitmap(bitmap);


                            //loat angulo = Float.parseFloat(edtGradosRotar.getText().toString());

                            //TransformacionRotarBitmap transformacionRotarBitmap = new TransformacionRotarBitmap(this, angulo);

                            //outputStream.write(PrintBitmap.POS_PrintBMP(transformacionRotarBitmap.transform(null, bitmap, 0, 0), ANCHO_IMG_58_MM, MODE_PRINT_IMG));

                            //saltos de liena
                            outputStream.write("\n\n\n\n".getBytes());

                        } catch (IOException e) {
                            Toast.makeText(this, "Error al intentar imprimir imagen", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "El angulo ingresado no es valido", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG_DEBUG, "Socket nulo");

                        txtLabel.setText("Impresora no conectada");
                    }
                } else {
                    Toast.makeText(this, "Debe dibujar primero", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_imprimir_img:
                if (rutaFoto != null) {
                    if (bluetoothSocket != null) {
                        try {

                            //imagen seleccionada a bitmap
                            Bitmap bitmap = BitmapFactory.decodeFile(rutaFoto);

                            //funciones para imprimir
                            printBitmap(bitmap);

                            // outputStream.write(PrintBitmap.POS_PrintBMP(bitmap, ANCHO_IMG_58_MM, MODE_PRINT_IMG));

                            //Saltos de linea
                            outputStream.write("\n\n\n\n".getBytes());

                        } catch (IOException e) {
                            Toast.makeText(this, "Error al intentar imprimir imagen", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "El angulo ingresado no es valido", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG_DEBUG, "Socket nulo");

                        txtLabel.setText("Impresora no conectada");
                    }
                } else {
                    Toast.makeText(this, "Debe tomar la imagen primero", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn_tomar_foto:
                if (pedirPermisosFaltantes()) {
                    AlertDialog.Builder builderAialogoAjusteHora = new AlertDialog.Builder(this);
                    builderAialogoAjusteHora.setMessage("Elija de donde tomar la imagen")
                            .setPositiveButton("CAMARA", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    try {
                                        cameraPhoto = new FotoDeCamara(getApplicationContext());

                                        Intent intentFoto = cameraPhoto.takePhotoIntent();
                                        startActivityForResult(intentFoto, INTENT_CAMARA);
                                        //cameraPhoto.addToGallery();
                                    } catch (IOException e) {
                                        Toast.makeText(MainActivity.this, "No se pudo iniciar la camara.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }).setNegativeButton("GALERIA", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            galleryPhoto = new GalleryPhoto(getApplicationContext());

                            Intent intentGaleria = galleryPhoto.openGalleryIntent();
                            startActivityForResult(intentGaleria, INTENT_GALERIA);
                        }
                    });
                    // creamos el dialogo
                    AlertDialog dialogo = builderAialogoAjusteHora.create();
                    dialogo.show();// mostramos el dialogo
                }
                break;
            case R.id.btn_dibujar:
                startActivityForResult(new Intent(this, DibujarActivity.class), IR_A_DIBUJAR);
                break;
            case R.id.btn_cerrar_conexion:
                cerrarConexion();

                break;
        }
    }

    /*-----Funciones para imprimir ESC/POS------------*/

    public void printBitmap(Bitmap bmp) throws IOException {
        bmp = compressPic(bmp);
        byte[] bmpByteArray = draw2PxPoint(bmp);
        printRawBytes(bmpByteArray);
    }

    public void printRawBytes(byte[] bytes) throws IOException {
        //imprimir bytes punto a punto
        outputStream.write(bytes);
        outputStream.flush();
    }

    private byte[] draw2PxPoint(Bitmap bmp) {
        //Tamaño de la imagen
        int size = bmp.getWidth() * bmp.getHeight() / 8 + 1000;
        byte[] tmp = new byte[size];
        int k = 0;
        // Espaciado entre lineas 0
        tmp[k++] = 0x1B;
        tmp[k++] = 0x33;
        tmp[k++] = 0x00;
        // Centrar impresion
        tmp[k++] = 0x1B;
        tmp[k++] = 0x61;
        tmp[k++] = 1;
        for (int j = 0; j < bmp.getHeight() / 24f; j++) {
            tmp[k++] = 0x1B;
            tmp[k++] = 0x2A;
            tmp[k++] = 33;
            tmp[k++] = (byte) (bmp.getWidth() % 256); // nL
            tmp[k++] = (byte) (bmp.getWidth() / 256); // nH
            for (int i = 0; i < bmp.getWidth(); i++) {
                for (int m = 0; m < 3; m++) {
                    for (int n = 0; n < 8; n++) {
                        byte b = px2Byte(i, j * 24 + m * 8 + n, bmp);
                        tmp[k] += tmp[k] + b;
                    }
                    k++;
                }
            }
            tmp[k++] = 10;
        }

        tmp[k++] = 0x1B;
        tmp[k++] = 0x32;

        byte[] result = new byte[k];
        System.arraycopy(tmp, 0, result, 0, k);
        return result;
    }
    private byte px2Byte(int x, int y, Bitmap bit) {
        if (x < bit.getWidth() && y < bit.getHeight()) {
            byte b;
            int pixel = bit.getPixel(x, y);
            int red = (pixel & 0x00ff0000) >> 16;
            int green = (pixel & 0x0000ff00) >> 8;
            int blue = pixel & 0x000000ff;
            int gray = RGB2Gray(red, green, blue);
            if (gray < 128) {
                b = 1;
            } else {
                b = 0;
            }
            return b;
        }
        return 0;
    }

    private int RGB2Gray(int r, int g, int b) {
        int gray = (int) (0.29900 * r + 0.58700 * g + 0.11400 * b); // Conversion de colores (Gris)
        return gray;
    }
    private Bitmap compressPic(Bitmap bitmapOrg) {
        // ancho y alto de la imagen
        int width = bitmapOrg.getWidth();
        int height = bitmapOrg.getHeight();
        // Definir ancho y alto de imagen, se puede modificar para el resultado deseado
        int newWidth = IMAGE_SIZE;
        int newHeight = IMAGE_SIZE;
        Bitmap targetBmp = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        Canvas targetCanvas = new Canvas(targetBmp);
        targetCanvas.drawColor(0xffffffff);
        targetCanvas.drawBitmap(bitmapOrg, new Rect(0, 0, width, height), new Rect(0, 0, newWidth, newHeight), null);
        return targetBmp;
    }
/*----Fin ESC/POS------------*/

    public void clickBuscarDispositivosSync(View btn) {
        // Cerramos la conexion antes de establecer otra
        cerrarConexion();

        Intent intentLista = new Intent(this, ListaBluetoohtActivity.class);
        startActivityForResult(intentLista, REQUEST_DISPOSITIVO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_DISPOSITIVO:
                    txtLabel.setText("Cargando...");

                    final String direccionDispositivo = data.getExtras().getString("DireccionDispositivo");
                    final String nombreDispositivo = data.getExtras().getString("NombreDispositivo");

                    // Obtenemos el dispositivo con la direccion seleccionada en la lista
                    dispositivoBluetooth = bluetoothAdapter.getRemoteDevice(direccionDispositivo);

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // Conectamos los dispositivos

                                // Creamos un socket
                                bluetoothSocket = dispositivoBluetooth.createRfcommSocketToServiceRecord(aplicacionUUID);
                                bluetoothSocket.connect();// conectamos el socket
                                outputStream = bluetoothSocket.getOutputStream();
                                inputStream = bluetoothSocket.getInputStream();

                                //empezarEscucharDatos();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        txtLabel.setText(nombreDispositivo + " conectada");
                                        Toast.makeText(MainActivity.this, "Dispositivo Conectado", Toast.LENGTH_SHORT).show();
                                    }
                                });

                            } catch (IOException e) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        txtLabel.setText("");
                                        Toast.makeText(MainActivity.this, "No se pudo conectar el dispositivo", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                Log.e(TAG_DEBUG, "Error al conectar el dispositivo bluetooth");

                                e.printStackTrace();
                            }
                        }
                    }).start();

                    break;
                case IR_A_DIBUJAR:
                    rutaImgDibujo = data.getExtras().getString("rutaImg");

                    Glide.with(this)
                            .load(rutaImgDibujo)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .transform(new TransformacionRotarBitmap(this, 90f))
                            .into(imgDibujo);


                    break;

                case INTENT_CAMARA:
                    try {
                        rutaFoto = cameraPhoto.getPhotoPath();

                        btnTomarFoto.setText("CAMBIAR IMAGEN");

                    } catch (NullPointerException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Error al cargar la foto, intente de nuevo.", Toast.LENGTH_SHORT).show();
                        btnTomarFoto.setText("TOMAR IMAGEN");

                    }
                    break;

                case INTENT_GALERIA:
                    galleryPhoto.setPhotoUri(data.getData());

                    // Si el peso es 0 Kb es porque la imagen no existe
                    if (ManipuladorImagen.pesoKBytesFile(galleryPhoto.getPath()) != 0) {
                        rutaFoto = galleryPhoto.getPath();
                        btnTomarFoto.setText("CAMBIAR IMAGEN");

                    } else {
                        Toast.makeText(this, "La imagen que eligió no es valida.", Toast.LENGTH_SHORT).show();
                        btnTomarFoto.setText("CAMBIAR IMAGEN");

                        return;// para que no ejecute el codigo siguiente
                    }
                    break;
            }

            if(rutaFoto != null){
                Constantes.crearRutaCarpetaImg();

                String fecha = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(new Date());

                String nombreImg = "IMG_IMPRIMIR_"+fecha;
                try {
                    File rutaDestino = Constantes.getRutaDestinoImg(nombreImg);

                    FileUtils.copyFile(new File(rutaFoto), rutaDestino);

                    Glide.with(getApplicationContext())
                            .load(rutaFoto)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .into(imgFoto);

                    // Debido a que la imagen se va a guardar en SipremImagenes ya no está en su carpeta original
                    rutaFoto = rutaDestino.getAbsolutePath();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "No se pudo guardar la imagen.", Toast.LENGTH_SHORT).show();
                }

            }
        }
    }

    private void cerrarConexion() {
        try {
            if (bluetoothSocket != null) {
                if (outputStream != null) outputStream.close();
                pararLectura = true;
                if (inputStream != null) inputStream.close();
                bluetoothSocket.close();
                txtLabel.setText("Conexion terminada");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * (font:A font:B)
     *
     * @param str
     * @param bold
     * @param font
     * @param widthsize
     * @param heigthsize
     * @return
     */
    public static byte[] getByteString(String str, int bold, int font, int widthsize, int heigthsize) {

        if (str.length() == 0 | widthsize < 0 | widthsize > 3 | heigthsize < 0 | heigthsize > 3
                | font < 0 | font > 1)
            return null;

        byte[] strData = null;
        try {
            strData = str.getBytes("iso-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        byte[] command = new byte[strData.length + 9];

        byte[] intToWidth = {0x00, 0x10, 0x20, 0x30};//
        byte[] intToHeight = {0x00, 0x01, 0x02, 0x03};//

        command[0] = 27;// caracter ESC para darle comandos a la impresora
        command[1] = 69;
        command[2] = ((byte) bold);
        command[3] = 27;
        command[4] = 77;
        command[5] = ((byte) font);
        command[6] = 29;
        command[7] = 33;
        command[8] = (byte) (intToWidth[widthsize] + intToHeight[heigthsize]);

        System.arraycopy(strData, 0, command, 9, strData.length);
        return command;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cerrarConexion();
    }

    public class TransformacionRotarBitmap extends BitmapTransformation {

        private float anguloRotar = 0f;

        public TransformacionRotarBitmap(Context context, float anguloRotar) {
            super(context);

            this.anguloRotar = anguloRotar;
        }

        @Override
        protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
            Matrix matrix = new Matrix();

            matrix.postRotate(anguloRotar);

            return Bitmap.createBitmap(toTransform, 0, 0, toTransform.getWidth(), toTransform.getHeight(), matrix, true);
        }

        @Override
        public String getId() {
            return "rotar" + anguloRotar;
        }
    }

    /**
     * Chequea cuales permisos faltan y los pide
     *
     * @return false si hay algun permiso faltante
     */
    private boolean pedirPermisosFaltantes() {
        boolean todosConsedidos = true;
        ArrayList<String> permisosFaltantes = new ArrayList<>();

        boolean permisoCamera = (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED);

        boolean permisoEscrituraSD = (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED);

        boolean permisoLecturaSD = (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED);


        if (!permisoCamera) {
            todosConsedidos = false;
            permisosFaltantes.add(Manifest.permission.CAMERA);
        }

        if (!permisoEscrituraSD) {
            todosConsedidos = false;
            permisosFaltantes.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!permisoLecturaSD) {
            todosConsedidos = false;
            permisosFaltantes.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!todosConsedidos) {
            String[] permisos = new String[permisosFaltantes.size()];
            permisos = permisosFaltantes.toArray(permisos);

            ActivityCompat.requestPermissions(this, permisos, COD_PERMISOS);
        }

        return todosConsedidos;
    }
}
