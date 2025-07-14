use crate::models::*;
use anyhow::{Result, anyhow};
use cpal::{Device, Host, Stream, StreamConfig, SupportedStreamConfig};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
use tokio::sync::{Mutex, mpsc};
use std::collections::VecDeque;

pub struct VoiceCallManager {
    host: Host,
    input_device: Option<Device>,
    output_device: Option<Device>,
    input_stream: Arc<Mutex<Option<Stream>>>,
    output_stream: Arc<Mutex<Option<Stream>>>,
    is_recording: Arc<AtomicBool>,
    is_playing: Arc<AtomicBool>,
    current_call: Arc<Mutex<Option<VoiceCall>>>,
    call_state: Arc<Mutex<CallState>>,
    audio_sender: Arc<Mutex<Option<mpsc::UnboundedSender<Vec<f32>>>>>,
    audio_receiver: Arc<Mutex<Option<mpsc::UnboundedReceiver<Vec<f32>>>>>,
    playback_buffer: Arc<Mutex<VecDeque<f32>>>,
}

#[derive(Debug, Clone)]
pub struct VoiceCall {
    pub call_id: String,
    pub contact: Contact,
    pub is_incoming: bool,
    pub start_time: i64,
    pub encryption_key: Option<Vec<u8>>,
}

impl VoiceCallManager {
    pub fn new() -> Self {
        let host = cpal::default_host();
        let (audio_sender, audio_receiver) = mpsc::unbounded_channel();
        
        Self {
            host,
            input_device: None,
            output_device: None,
            input_stream: Arc::new(Mutex::new(None)),
            output_stream: Arc::new(Mutex::new(None)),
            is_recording: Arc::new(AtomicBool::new(false)),
            is_playing: Arc::new(AtomicBool::new(false)),
            current_call: Arc::new(Mutex::new(None)),
            call_state: Arc::new(Mutex::new(CallState::Idle)),
            audio_sender: Arc::new(Mutex::new(Some(audio_sender))),
            audio_receiver: Arc::new(Mutex::new(Some(audio_receiver))),
            playback_buffer: Arc::new(Mutex::new(VecDeque::new())),
        }
    }

    pub async fn initialize_audio_devices(&mut self) -> Result<()> {
        // Get default input device (microphone)
        self.input_device = self.host.default_input_device();
        if self.input_device.is_none() {
            return Err(anyhow!("No input device available"));
        }

        // Get default output device (speakers/headphones)
        self.output_device = self.host.default_output_device();
        if self.output_device.is_none() {
            return Err(anyhow!("No output device available"));
        }

        log::info!("Audio devices initialized successfully");
        Ok(())
    }

    pub async fn initiate_call(&mut self, contact: &Contact) -> Result<String> {
        if !matches!(*self.call_state.lock().await, CallState::Idle) {
            return Err(anyhow!("Already in a call"));
        }

        let call_id = format!("call_{}_{}", 
            chrono::Utc::now().timestamp(), 
            rand::random::<u32>()
        );

        let call = VoiceCall {
            call_id: call_id.clone(),
            contact: contact.clone(),
            is_incoming: false,
            start_time: chrono::Utc::now().timestamp(),
            encryption_key: None,
        };

        {
            let mut current_call = self.current_call.lock().await;
            *current_call = Some(call);
        }

        {
            let mut state = self.call_state.lock().await;
            *state = CallState::Calling;
        }

        // Initialize audio devices if not already done
        if self.input_device.is_none() || self.output_device.is_none() {
            self.initialize_audio_devices().await?;
        }

        log::info!("Voice call initiated: {}", call_id);
        Ok(call_id)
    }

    pub async fn accept_call(&mut self, call_id: &str) -> Result<()> {
        let current_call = {
            let call = self.current_call.lock().await;
            call.clone()
        };

        match current_call {
            Some(call) if call.call_id == call_id => {
                {
                    let mut state = self.call_state.lock().await;
                    *state = CallState::Connected;
                }

                self.start_audio_streaming().await?;
                log::info!("Voice call accepted: {}", call_id);
                Ok(())
            }
            _ => Err(anyhow!("No matching call to accept"))
        }
    }

    pub async fn reject_call(&mut self, call_id: &str) -> Result<()> {
        let current_call = {
            let call = self.current_call.lock().await;
            call.clone()
        };

        match current_call {
            Some(call) if call.call_id == call_id => {
                {
                    let mut state = self.call_state.lock().await;
                    *state = CallState::Ended;
                }

                {
                    let mut current_call = self.current_call.lock().await;
                    *current_call = None;
                }

                log::info!("Voice call rejected: {}", call_id);
                Ok(())
            }
            _ => Err(anyhow!("No matching call to reject"))
        }
    }

    pub async fn end_call(&mut self) -> Result<()> {
        self.stop_audio_streaming().await?;

        {
            let mut state = self.call_state.lock().await;
            *state = CallState::Ended;
        }

        {
            let mut current_call = self.current_call.lock().await;
            *current_call = None;
        }

        log::info!("Voice call ended");
        Ok(())
    }

    pub async fn get_status(&self) -> Result<CallStatus> {
        let state = {
            let state = self.call_state.lock().await;
            state.clone()
        };

        let current_call = {
            let call = self.current_call.lock().await;
            call.clone()
        };

        let status = match current_call {
            Some(call) => CallStatus {
                state: format!("{:?}", state),
                call_id: Some(call.call_id),
                contact_id: Some(call.contact.id),
                start_time: Some(call.start_time),
                duration: Some(chrono::Utc::now().timestamp() - call.start_time),
                is_incoming: call.is_incoming,
            },
            None => CallStatus {
                state: format!("{:?}", state),
                call_id: None,
                contact_id: None,
                start_time: None,
                duration: None,
                is_incoming: false,
            }
        };

        Ok(status)
    }

    async fn start_audio_streaming(&mut self) -> Result<()> {
        self.start_recording().await?;
        self.start_playback().await?;
        log::info!("Audio streaming started");
        Ok(())
    }

    async fn stop_audio_streaming(&mut self) -> Result<()> {
        self.is_recording.store(false, Ordering::Relaxed);
        self.is_playing.store(false, Ordering::Relaxed);

        {
            let mut input_stream = self.input_stream.lock().await;
            if let Some(stream) = input_stream.take() {
                drop(stream);
            }
        }

        {
            let mut output_stream = self.output_stream.lock().await;
            if let Some(stream) = output_stream.take() {
                drop(stream);
            }
        }

        log::info!("Audio streaming stopped");
        Ok(())
    }

    async fn start_recording(&mut self) -> Result<()> {
        let input_device = self.input_device.as_ref()
            .ok_or_else(|| anyhow!("No input device available"))?;

        let config = input_device.default_input_config()?;
        let sample_rate = config.sample_rate().0;
        let channels = config.channels();

        log::info!("Recording config: {} Hz, {} channels", sample_rate, channels);

        let is_recording = Arc::clone(&self.is_recording);
        let audio_sender = Arc::clone(&self.audio_sender);

        let stream = match config.sample_format() {
            cpal::SampleFormat::F32 => {
                input_device.build_input_stream(
                    &config.into(),
                    move |data: &[f32], _: &cpal::InputCallbackInfo| {
                        if is_recording.load(Ordering::Relaxed) {
                            let sender = audio_sender.blocking_lock();
                            if let Some(ref sender) = *sender {
                                let _ = sender.send(data.to_vec());
                            }
                        }
                    },
                    |err| log::error!("Audio input error: {}", err),
                    None,
                )?
            }
            cpal::SampleFormat::I16 => {
                input_device.build_input_stream(
                    &config.into(),
                    move |data: &[i16], _: &cpal::InputCallbackInfo| {
                        if is_recording.load(Ordering::Relaxed) {
                            let float_data: Vec<f32> = data.iter()
                                .map(|&sample| sample as f32 / i16::MAX as f32)
                                .collect();
                            
                            let sender = audio_sender.blocking_lock();
                            if let Some(ref sender) = *sender {
                                let _ = sender.send(float_data);
                            }
                        }
                    },
                    |err| log::error!("Audio input error: {}", err),
                    None,
                )?
            }
            cpal::SampleFormat::U16 => {
                input_device.build_input_stream(
                    &config.into(),
                    move |data: &[u16], _: &cpal::InputCallbackInfo| {
                        if is_recording.load(Ordering::Relaxed) {
                            let float_data: Vec<f32> = data.iter()
                                .map(|&sample| (sample as f32 - u16::MAX as f32 / 2.0) / (u16::MAX as f32 / 2.0))
                                .collect();
                            
                            let sender = audio_sender.blocking_lock();
                            if let Some(ref sender) = *sender {
                                let _ = sender.send(float_data);
                            }
                        }
                    },
                    |err| log::error!("Audio input error: {}", err),
                    None,
                )?
            }
        };

        stream.play()?;
        self.is_recording.store(true, Ordering::Relaxed);

        {
            let mut input_stream = self.input_stream.lock().await;
            *input_stream = Some(stream);
        }

        log::info!("Recording started");
        Ok(())
    }

    async fn start_playback(&mut self) -> Result<()> {
        let output_device = self.output_device.as_ref()
            .ok_or_else(|| anyhow!("No output device available"))?;

        let config = output_device.default_output_config()?;
        let sample_rate = config.sample_rate().0;
        let channels = config.channels();

        log::info!("Playback config: {} Hz, {} channels", sample_rate, channels);

        let is_playing = Arc::clone(&self.is_playing);
        let playback_buffer = Arc::clone(&self.playback_buffer);

        let stream = match config.sample_format() {
            cpal::SampleFormat::F32 => {
                output_device.build_output_stream(
                    &config.into(),
                    move |data: &mut [f32], _: &cpal::OutputCallbackInfo| {
                        if is_playing.load(Ordering::Relaxed) {
                            let mut buffer = playback_buffer.blocking_lock();
                            for sample in data.iter_mut() {
                                *sample = buffer.pop_front().unwrap_or(0.0);
                            }
                        } else {
                            for sample in data.iter_mut() {
                                *sample = 0.0;
                            }
                        }
                    },
                    |err| log::error!("Audio output error: {}", err),
                    None,
                )?
            }
            cpal::SampleFormat::I16 => {
                output_device.build_output_stream(
                    &config.into(),
                    move |data: &mut [i16], _: &cpal::OutputCallbackInfo| {
                        if is_playing.load(Ordering::Relaxed) {
                            let mut buffer = playback_buffer.blocking_lock();
                            for sample in data.iter_mut() {
                                let float_sample = buffer.pop_front().unwrap_or(0.0);
                                *sample = (float_sample * i16::MAX as f32) as i16;
                            }
                        } else {
                            for sample in data.iter_mut() {
                                *sample = 0;
                            }
                        }
                    },
                    |err| log::error!("Audio output error: {}", err),
                    None,
                )?
            }
            cpal::SampleFormat::U16 => {
                output_device.build_output_stream(
                    &config.into(),
                    move |data: &mut [u16], _: &cpal::OutputCallbackInfo| {
                        if is_playing.load(Ordering::Relaxed) {
                            let mut buffer = playback_buffer.blocking_lock();
                            for sample in data.iter_mut() {
                                let float_sample = buffer.pop_front().unwrap_or(0.0);
                                *sample = ((float_sample + 1.0) * u16::MAX as f32 / 2.0) as u16;
                            }
                        } else {
                            for sample in data.iter_mut() {
                                *sample = u16::MAX / 2;
                            }
                        }
                    },
                    |err| log::error!("Audio output error: {}", err),
                    None,
                )?
            }
        };

        stream.play()?;
        self.is_playing.store(true, Ordering::Relaxed);

        {
            let mut output_stream = self.output_stream.lock().await;
            *output_stream = Some(stream);
        }

        log::info!("Playback started");
        Ok(())
    }

    pub async fn add_audio_data(&self, audio_data: Vec<f32>) -> Result<()> {
        let mut buffer = self.playback_buffer.lock().await;
        buffer.extend(audio_data);
        
        // Limit buffer size to prevent memory issues
        while buffer.len() > 48000 { // ~1 second at 48kHz
            buffer.pop_front();
        }
        
        Ok(())
    }

    pub async fn get_available_devices(&self) -> Result<(Vec<String>, Vec<String>)> {
        let input_devices: Vec<String> = self.host.input_devices()?
            .filter_map(|device| device.name().ok())
            .collect();

        let output_devices: Vec<String> = self.host.output_devices()?
            .filter_map(|device| device.name().ok())
            .collect();

        Ok((input_devices, output_devices))
    }
}
