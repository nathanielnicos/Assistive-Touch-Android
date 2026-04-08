import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Assistive Touch Android',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  HomePageState createState() => HomePageState();
}

class HomePageState extends State<HomePage> with WidgetsBindingObserver {
  static const platform = MethodChannel('assistive_touch_channel');
  bool _isServiceRunning = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    platform.setMethodCallHandler((call) async {
      switch (call.method) {
        case "serviceStatus":
          setState(() {
            _isServiceRunning = call.arguments as bool;
          });
          break;
      }
    });

    _checkInitialStatus();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkInitialStatus();
    }
  }

  Future<void> _checkInitialStatus() async {
    try {
      final bool status = await platform.invokeMethod('checkServiceStatus');

      if (mounted) {
        setState(() {
          _isServiceRunning = status;
        });
      }
    } catch (e) {
      debugPrint("Gagal cek status awal: $e");
    }
  }

  Future<void> _toggleService() async {
    try {
      if (_isServiceRunning) {
        await platform.invokeMethod('stopService');

        setState(() {
          _isServiceRunning = false;
        });
      } else {
        await platform.invokeMethod('startService');
      }
    } on PlatformException catch (e) {
      _showErrorSnackBar("Gagal mengontrol service: ${e.message}");
    }
  }

  void _showErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), backgroundColor: Colors.redAccent),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Assistive Touch Android"), centerTitle: true),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Status Icon dengan Animasi sederhana
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 300),
                child: Icon(
                  _isServiceRunning ? Icons.check_circle : Icons.power_settings_new,
                  key: ValueKey<bool>(_isServiceRunning), // Penting agar animasi terpicu
                  size: 100,
                  color: _isServiceRunning ? Colors.greenAccent : Colors.grey,
                ),
              ),

              const SizedBox(height: 20),
              
              Text(
                _isServiceRunning ? "Layanan Aktif" : "Layanan Nonaktif",
                style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
              ),
              
              const SizedBox(height: 10),
              
              Text(
                _isServiceRunning 
                  ? "Tombol melayang siap digunakan." 
                  : "Aktifkan layanan untuk memunculkan tombol melayang.",
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white60),
              ),
              
              const SizedBox(height: 40),

              // Tombol Utama
              SizedBox(
                width: double.infinity,
                height: 60,
                child: ElevatedButton.icon(
                  onPressed: _toggleService,
                  icon: Icon(_isServiceRunning ? Icons.stop : Icons.play_arrow),
                  label: Text(_isServiceRunning ? "STOP SERVICE" : "START ASSISTIVE TOUCH"),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _isServiceRunning ? Colors.redAccent : Colors.blueAccent,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}