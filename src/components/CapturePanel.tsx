import { CameraView, useCameraPermissions } from 'expo-camera';
import * as Location from 'expo-location';
import { useMemo, useRef, useState } from 'react';
import { Image, Pressable, ScrollView, Text, TextInput, View } from 'react-native';

import { speciesCandidates } from '../data/kamiyama';
import { describeEnvironment, inferRarity, suggestCandidates } from '../lib/geo';
import type { LatLng, Observation, SpeciesCandidate, SpeciesCategory } from '../types/domain';

type CapturePanelProps = {
  currentLocation: LatLng | null;
  onObservationSaved: (observation: Observation) => void;
  onLocationResolved: (location: LatLng) => void;
};

type CapturedPhoto = {
  uri: string;
  location: LatLng;
  accuracy: number | null;
  observedAt: Date;
};

export function CapturePanel({
  currentLocation,
  onObservationSaved,
  onLocationResolved,
}: CapturePanelProps) {
  const cameraRef = useRef<CameraView>(null);
  const [permission, requestPermission] = useCameraPermissions();
  const [category, setCategory] = useState<SpeciesCategory>('plant');
  const [photo, setPhoto] = useState<CapturedPhoto | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState<SpeciesCandidate | null>(null);
  const [customName, setCustomName] = useState('');
  const [note, setNote] = useState('');
  const [isBusy, setIsBusy] = useState(false);
  const [message, setMessage] = useState('写真を撮ると、GPSと時刻から観察ピンを作ります。');

  const suggestions = useMemo(() => {
    if (!photo) {
      return [];
    }
    return suggestCandidates(speciesCandidates, category, photo.location, photo.observedAt);
  }, [category, photo]);

  async function takePhoto() {
    if (!permission?.granted) {
      await requestPermission();
      return;
    }

    setIsBusy(true);
    setMessage('カメラと位置情報を確認しています。');
    try {
      const location = await resolveLocation();
      const captured = await cameraRef.current?.takePictureAsync({
        base64: false,
        quality: 0.72,
        skipProcessing: false,
      });

      if (!captured?.uri) {
        throw new Error('写真を取得できませんでした。');
      }

      const nextPhoto = {
        uri: captured.uri,
        location,
        accuracy: null,
        observedAt: new Date(),
      };
      setPhoto(nextPhoto);
      setSelectedCandidate(null);
      setCustomName('');
      setMessage('候補を選ぶか、名前を入力して図鑑に登録できます。');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '撮影に失敗しました。');
    } finally {
      setIsBusy(false);
    }
  }

  async function save() {
    if (!photo) {
      return;
    }

    const observedAt = photo.observedAt;
    const name = customName.trim() || selectedCandidate?.commonName || '未同定';
    const location = photo.location;
    const observation: Observation = {
      id: `${observedAt.getTime()}-${Math.random().toString(36).slice(2)}`,
      photoUri: photo.uri,
      category,
      candidateId: selectedCandidate?.id ?? null,
      customName: name,
      note: note.trim(),
      latitude: location.latitude,
      longitude: location.longitude,
      accuracy: photo.accuracy,
      observedAt: observedAt.toISOString(),
      environment: describeEnvironment(location),
      rarity: inferRarity(selectedCandidate, location, observedAt),
    };

    await onObservationSaved(observation);
    setPhoto(null);
    setSelectedCandidate(null);
    setCustomName('');
    setNote('');
    setMessage('図鑑に登録しました。地図にあなたの発見ピンが増えています。');
  }

  async function resolveLocation(): Promise<LatLng> {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      if (currentLocation) {
        return currentLocation;
      }
      throw new Error('位置情報の許可がないため、観察地点を記録できません。');
    }
    const result = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });
    const location = {
      latitude: result.coords.latitude,
      longitude: result.coords.longitude,
    };
    onLocationResolved(location);
    return location;
  }

  return (
    <ScrollView contentContainerStyle={{ paddingBottom: 120 }}>
      <View style={{ gap: 14 }}>
        <View style={{ flexDirection: 'row', gap: 8 }}>
          <Pressable
            onPress={() => setCategory('plant')}
            style={({ pressed }) => [
              styles.segment,
              category === 'plant' && styles.segmentActive,
              pressed && styles.pressed,
            ]}
          >
            <Text style={[styles.segmentText, category === 'plant' && styles.segmentTextActive]}>
              植物
            </Text>
          </Pressable>
          <Pressable
            onPress={() => setCategory('insect')}
            style={({ pressed }) => [
              styles.segment,
              category === 'insect' && styles.segmentActive,
              pressed && styles.pressed,
            ]}
          >
            <Text style={[styles.segmentText, category === 'insect' && styles.segmentTextActive]}>
              虫
            </Text>
          </Pressable>
        </View>

        <View style={styles.cameraBox}>
          {permission?.granted ? (
            <CameraView ref={cameraRef} style={styles.camera} facing="back" animateShutter />
          ) : (
            <View style={styles.permissionBox}>
              <Text style={styles.permissionText}>カメラの許可が必要です。</Text>
              <Pressable onPress={requestPermission} style={styles.primaryButton}>
                <Text style={styles.primaryButtonText}>許可する</Text>
              </Pressable>
            </View>
          )}
        </View>

        <Pressable
          onPress={takePhoto}
          disabled={isBusy}
          style={({ pressed }) => [
            styles.shutterButton,
            (pressed || isBusy) && styles.pressed,
          ]}
        >
          <Text style={styles.shutterButtonText}>{isBusy ? '記録中...' : '撮影して地点を記録'}</Text>
        </Pressable>

        <Text style={styles.message}>{message}</Text>

        {photo ? (
          <View style={styles.reviewPanel}>
            <Image source={{ uri: photo.uri }} style={styles.previewImage} />
            <Text style={styles.metaText}>
              {photo.observedAt.toLocaleString('ja-JP')} / {describeEnvironment(photo.location)}
            </Text>

            <Text style={styles.sectionTitle}>候補</Text>
            <View style={{ gap: 8 }}>
              {suggestions.map(({ candidate, distance }) => (
                <Pressable
                  key={candidate.id}
                  onPress={() => {
                    setSelectedCandidate(candidate);
                    setCustomName(candidate.commonName);
                  }}
                  style={[
                    styles.candidateRow,
                    selectedCandidate?.id === candidate.id && styles.candidateRowActive,
                  ]}
                >
                  <View style={{ flex: 1 }}>
                    <Text style={styles.candidateName}>{candidate.commonName}</Text>
                    <Text style={styles.candidateSci}>{candidate.scientificName}</Text>
                    <Text style={styles.candidateHint}>{candidate.hint}</Text>
                  </View>
                  <Text style={styles.distanceText}>{Math.round(distance)}m</Text>
                </Pressable>
              ))}
            </View>

            <TextInput
              value={customName}
              onChangeText={setCustomName}
              placeholder="生き物名"
              style={styles.input}
            />
            <TextInput
              value={note}
              onChangeText={setNote}
              placeholder="メモ"
              multiline
              style={[styles.input, styles.noteInput]}
            />
            <Pressable onPress={save} style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>図鑑に登録</Text>
            </Pressable>
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

const styles = {
  segment: {
    flex: 1,
    minHeight: 42,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#cbd7ca',
    backgroundColor: '#f7faf5',
  },
  segmentActive: {
    backgroundColor: '#1f4f3a',
    borderColor: '#1f4f3a',
  },
  segmentText: {
    color: '#314238',
    fontSize: 15,
    fontWeight: '700' as const,
  },
  segmentTextActive: {
    color: '#ffffff',
  },
  cameraBox: {
    height: 330,
    overflow: 'hidden' as const,
    borderRadius: 8,
    backgroundColor: '#1b211d',
  },
  camera: {
    flex: 1,
  },
  permissionBox: {
    flex: 1,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    gap: 16,
    padding: 24,
  },
  permissionText: {
    color: '#f7faf5',
    fontSize: 16,
  },
  shutterButton: {
    minHeight: 54,
    borderRadius: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: '#e05a47',
  },
  shutterButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '800' as const,
  },
  primaryButton: {
    minHeight: 48,
    borderRadius: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: '#246b4b',
    paddingHorizontal: 16,
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '800' as const,
  },
  pressed: {
    opacity: 0.75,
  },
  message: {
    color: '#59675e',
    lineHeight: 20,
  },
  reviewPanel: {
    gap: 12,
    paddingTop: 4,
  },
  previewImage: {
    width: '100%' as const,
    aspectRatio: 1,
    borderRadius: 8,
    backgroundColor: '#e5ebe6',
  },
  metaText: {
    color: '#59675e',
    fontSize: 13,
  },
  sectionTitle: {
    color: '#14231a',
    fontSize: 16,
    fontWeight: '800' as const,
  },
  candidateRow: {
    flexDirection: 'row' as const,
    gap: 12,
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#d7e0d8',
    backgroundColor: '#ffffff',
  },
  candidateRowActive: {
    borderColor: '#246b4b',
    backgroundColor: '#eef7f0',
  },
  candidateName: {
    color: '#14231a',
    fontWeight: '800' as const,
    fontSize: 15,
  },
  candidateSci: {
    color: '#617068',
    fontSize: 12,
    fontStyle: 'italic' as const,
    marginTop: 2,
  },
  candidateHint: {
    color: '#3d4d43',
    marginTop: 6,
    lineHeight: 18,
  },
  distanceText: {
    color: '#246b4b',
    fontWeight: '800' as const,
    minWidth: 56,
    textAlign: 'right' as const,
  },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: '#cbd7ca',
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 15,
    backgroundColor: '#ffffff',
  },
  noteInput: {
    minHeight: 88,
    paddingTop: 12,
  },
};
