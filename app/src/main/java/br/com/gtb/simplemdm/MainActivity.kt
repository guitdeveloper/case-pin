package br.com.gtb.simplemdm

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gtb.simplemdm.ui.theme.SimplemdmTheme
import java.io.IOException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

class MainActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var receiverComponentName: ComponentName

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimplemdmTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppScreen(
                        onChangePinFromSim = { changePinFromSim() },
                        onLockDevice = { lockDevice() },
                        onChangeScreenPassword = { changeScreenPassword() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
        initView()
    }

    private fun initView() {
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        receiverComponentName = ComponentName(this, SimpleDeviceAdminReceiver::class.java)
        if (devicePolicyManager.isDeviceOwnerApp(packageName)){
            setDevicePolicies()
        }
    }

    private fun setDevicePolicies(active: Boolean = true) {
        setRestriction(UserManager.DISALLOW_ADJUST_VOLUME, active)
        setRestriction(UserManager.DISALLOW_CONFIG_WIFI, active)
        setRestriction(UserManager.DISALLOW_SAFE_BOOT, active)
        setRestriction(UserManager.DISALLOW_FACTORY_RESET, active)

        devicePolicyManager.setKeyguardDisabled(receiverComponentName, active)
        devicePolicyManager.setStatusBarDisabled(receiverComponentName, active)

        if (active) {
            devicePolicyManager.setSystemUpdatePolicy(receiverComponentName, SystemUpdatePolicy.createWindowedInstallPolicy(60, 120))
        } else {
            devicePolicyManager.setSystemUpdatePolicy(receiverComponentName,null)
        }

        configureLockTask(active)
    }

    private fun setRestriction(restriction: String, disallow: Boolean) {
        if (disallow) {
            devicePolicyManager.addUserRestriction(receiverComponentName, restriction)
        } else {
            devicePolicyManager.clearUserRestriction(receiverComponentName, restriction)
        }
    }

    private fun configureLockTask(active: Boolean) {
        devicePolicyManager.setLockTaskPackages(
            receiverComponentName,
            if (active) {
                arrayOf(packageName)
            } else {
                arrayOf()
            }
        )

        val intentFilter = IntentFilter(Intent.ACTION_MAIN)
        intentFilter.addCategory(Intent.CATEGORY_HOME)
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT)

        if (active) {
            devicePolicyManager.addPersistentPreferredActivity(
                receiverComponentName,
                intentFilter,
                ComponentName(packageName, PersistentPreferredActivity::class.java.name)
            )
        } else {
            devicePolicyManager.clearPackagePersistentPreferredActivities(
                receiverComponentName,
                packageName
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun changePinFromSim() {
        // Abre a tela que tem o acesso para a tela do SIM lock
//        val intent = Intent("com.android.settings.MORE_SECURITY_PRIVACY_SETTINGS")
//        startActivity(intent)

        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            outputStream.write("am start -a android.intent.action.MAIN -n com.android.settings/.Settings\$IccLockSettingsActivity".toByteArray())
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        addOverlay()
    }

    private fun lockDevice() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 1000)
        }
        if (devicePolicyManager.isAdminActive(receiverComponentName)) {
            devicePolicyManager.lockNow()
        } else {
            Toast.makeText(this, "Admin não está ativo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateRandomPasswordToken(): ByteArray? {
        try {
            return SecureRandom.getInstance("SHA1PRNG").generateSeed(32)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun changeScreenPassword() {
        if (devicePolicyManager.isAdminActive(receiverComponentName)) {
            val token = generateRandomPasswordToken()
            val devicePolicyManager = applicationContext.getSystemService(
                DEVICE_POLICY_SERVICE
            ) as DevicePolicyManager
            val newPassword = "4321"
            val keyguardManager = this.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.createConfirmDeviceCredentialIntent(null, null)
            devicePolicyManager.setResetPasswordToken(receiverComponentName, token)
            devicePolicyManager.resetPasswordWithToken(
                receiverComponentName,
                newPassword,
                token,
                0
            )
            Toast.makeText(this, "Senha alterada para: $newPassword", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Admin não está ativo", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun addOverlay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlayView, layoutParams)

        val closeButton: Button = overlayView.findViewById(R.id.overlay_button_close)
        closeButton.setOnClickListener {
            windowManager.removeView(overlayView)
        }
    }
}

@Composable
fun AppScreen(
    onChangePinFromSim: () -> Unit,
    onLockDevice: () -> Unit,
    onChangeScreenPassword: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Simple MDM", fontSize = 24.sp, style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onChangePinFromSim, modifier = Modifier.fillMaxWidth()) {
            Text("Alterar PIN do chip")
        }

        Spacer(modifier = modifier)

        Button(onClick = onLockDevice, modifier = Modifier.fillMaxWidth()) {
            Text("Bloquear Dispositivo")
        }

        Spacer(modifier = modifier)

        Button(onClick = onChangeScreenPassword, modifier = Modifier.fillMaxWidth()) {
            Text("Alterar Senha")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SimplemdmTheme {
        AppScreen(
            onChangePinFromSim = {},
            onLockDevice = {},
            onChangeScreenPassword = {},
        )
    }
}