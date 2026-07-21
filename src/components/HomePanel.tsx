import {
  BookOpen,
  Camera,
  ChevronRight,
  MapPinned,
  RefreshCw,
  Sparkles,
  Wifi,
} from 'lucide-react-native';
import type { ReactNode } from 'react';
import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import { featuredSpeciesIds, speciesImages } from '../data/speciesImages';
import { speciesCandidates } from '../data/kamiyama';
import { colors, radius, shadow } from '../styles/theme';
import type { Observation } from '../types/domain';

type HomePanelProps = {
  observations: Observation[];
  reviewCount: number;
  statusText: string;
  onOpenMap: (observation?: Observation) => void;
  onOpenEncyclopedia: () => void;
  onOpenReview: () => void;
};

export function HomePanel({
  observations,
  reviewCount,
  statusText,
  onOpenMap,
  onOpenEncyclopedia,
  onOpenReview,
}: HomePanelProps) {
  const discoveredSpecies = new Set(observations.map((item) => item.candidateId).filter(Boolean));
  const rareCount = observations.filter((item) => item.rarity === 'rare' || item.rarity === 'special').length;
  const recent = observations.slice(0, 4);

  return (
    <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
      <View style={styles.greetingRow}>
        <View style={{ flex: 1 }}>
          <Text style={styles.eyebrow}>今日の神山</Text>
          <Text style={styles.greeting}>どんな発見に会えるかな？</Text>
        </View>
        <View style={styles.dayBadge}>
          <Text style={styles.dayBadgeTop}>{new Date().getDate()}</Text>
          <Text style={styles.dayBadgeBottom}>{new Date().toLocaleDateString('ja-JP', { month: 'short' })}</Text>
        </View>
      </View>

      <View style={styles.syncPanel}>
        <View style={styles.deviceIcon}>
          <Camera color={colors.forestDark} size={24} strokeWidth={2.2} />
        </View>
        <View style={{ flex: 1 }}>
          <View style={styles.syncTitleRow}>
            <Text style={styles.syncTitle}>THINKLET 同期</Text>
            <View style={styles.onlineDot} />
          </View>
          <Text style={styles.syncText} numberOfLines={2}>{statusText}</Text>
        </View>
        <Wifi color="#DDF3DF" size={22} />
      </View>

      {reviewCount > 0 ? (
        <Pressable onPress={onOpenReview} style={({ pressed }) => [styles.reviewNotice, pressed && styles.pressed]}>
          <View style={styles.reviewNoticeIcon}>
            <Sparkles color={colors.amber} size={21} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.reviewNoticeTitle}>AIから候補が届いたよ</Text>
            <Text style={styles.reviewNoticeText}>{reviewCount}件の写真をいっしょに確認しよう</Text>
          </View>
          <ChevronRight color={colors.inkSoft} size={20} />
        </Pressable>
      ) : null}

      <SectionHeader title="これまでの発見" action="図鑑を見る" onPress={onOpenEncyclopedia} />
      <View style={styles.statsRow}>
        <Stat value={observations.length.toString()} label="観察した数" tint={colors.white} />
        <Stat value={discoveredSpecies.size.toString()} label="発見種類" tint={colors.leafPale} />
        <Stat value={rareCount.toString()} label="レア発見" tint={colors.amberPale} />
      </View>

      {recent.length > 0 ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.recentRow}>
          {recent.map((observation) => (
            <Pressable
              key={observation.id}
              onPress={() => onOpenMap(observation)}
              style={({ pressed }) => [styles.recentItem, pressed && styles.pressed]}
            >
              <Image source={{ uri: observation.photoUri }} style={styles.recentImage} />
              <Text style={styles.recentTime}>{formatTime(observation.observedAt)}</Text>
              <Text style={styles.recentName} numberOfLines={1}>{observation.customName}</Text>
            </Pressable>
          ))}
        </ScrollView>
      ) : (
        <View style={styles.emptyDiscovery}>
          <Sparkles color={colors.leaf} size={26} />
          <View style={{ flex: 1 }}>
            <Text style={styles.emptyDiscoveryTitle}>最初の一枚を待っています</Text>
            <Text style={styles.emptyDiscoveryText}>THINKLETで撮影すると、ここに発見が並びます。</Text>
          </View>
        </View>
      )}

      <SectionHeader title="神山で探せる生き物" action="マップを見る" onPress={() => onOpenMap()} />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.featuredRow}>
        {featuredSpeciesIds.map((id) => {
          const candidate = speciesCandidates.find((item) => item.id === id);
          if (!candidate) return null;
          return (
            <View key={candidate.id} style={styles.featuredItem}>
              <Image source={speciesImages[candidate.id]} style={styles.featuredImage} />
              <View style={styles.featuredBody}>
                <Text style={styles.featuredName}>{candidate.commonName}</Text>
                <Text style={styles.featuredHint} numberOfLines={2}>{candidate.hint}</Text>
              </View>
            </View>
          );
        })}
      </ScrollView>

      <Text style={styles.actionTitle}>どこを見てみる？</Text>
      <View style={styles.actionRow}>
        <QuickAction icon={<BookOpen color={colors.forest} size={25} />} label="図鑑" onPress={onOpenEncyclopedia} />
        <QuickAction icon={<MapPinned color={colors.forest} size={25} />} label="地図" onPress={() => onOpenMap()} />
        <QuickAction icon={<RefreshCw color={colors.forest} size={25} />} label="AI候補" onPress={onOpenReview} />
      </View>
    </ScrollView>
  );
}

function SectionHeader({ title, action, onPress }: { title: string; action: string; onPress: () => void }) {
  return (
    <View style={styles.sectionHeader}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <Pressable onPress={onPress} style={styles.sectionAction}>
        <Text style={styles.sectionActionText}>{action}</Text>
        <ChevronRight color={colors.forest} size={16} />
      </Pressable>
    </View>
  );
}

function Stat({ value, label, tint }: { value: string; label: string; tint: string }) {
  return (
    <View style={[styles.stat, { backgroundColor: tint }]}>
      <Text style={styles.statValue}>{value}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </View>
  );
}

function QuickAction({ icon, label, onPress }: { icon: ReactNode; label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.quickAction, pressed && styles.pressed]}>
      {icon}
      <Text style={styles.quickActionLabel}>{label}</Text>
    </Pressable>
  );
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit' });
}

const styles = {
  scrollContent: { padding: 18, paddingBottom: 112, gap: 16 },
  greetingRow: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 16 },
  eyebrow: { color: colors.moss, fontSize: 12, fontWeight: '800' as const },
  greeting: { color: colors.ink, fontSize: 22, lineHeight: 30, fontWeight: '900' as const, marginTop: 3 },
  dayBadge: { width: 54, height: 54, borderRadius: radius.medium, backgroundColor: colors.leafPale, alignItems: 'center' as const, justifyContent: 'center' as const },
  dayBadgeTop: { color: colors.forestDark, fontSize: 20, lineHeight: 22, fontWeight: '900' as const },
  dayBadgeBottom: { color: colors.inkSoft, fontSize: 10, fontWeight: '700' as const },
  syncPanel: { minHeight: 104, padding: 16, borderRadius: radius.medium, backgroundColor: colors.forest, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 13, ...shadow },
  deviceIcon: { width: 52, height: 52, borderRadius: radius.medium, backgroundColor: '#E7F3E3', alignItems: 'center' as const, justifyContent: 'center' as const },
  syncTitleRow: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 7 },
  syncTitle: { color: colors.white, fontSize: 17, fontWeight: '900' as const },
  onlineDot: { width: 9, height: 9, borderRadius: 5, backgroundColor: '#7DE191' },
  syncText: { color: '#DCEADE', fontSize: 12, lineHeight: 17, marginTop: 5 },
  reviewNotice: { minHeight: 74, padding: 13, borderRadius: radius.medium, backgroundColor: colors.amberPale, borderWidth: 1, borderColor: '#F0D89D', flexDirection: 'row' as const, alignItems: 'center' as const, gap: 11 },
  reviewNoticeIcon: { width: 42, height: 42, borderRadius: radius.medium, backgroundColor: colors.white, alignItems: 'center' as const, justifyContent: 'center' as const },
  reviewNoticeTitle: { color: colors.ink, fontSize: 14, fontWeight: '900' as const },
  reviewNoticeText: { color: colors.inkSoft, fontSize: 12, marginTop: 3 },
  sectionHeader: { flexDirection: 'row' as const, alignItems: 'center' as const, marginTop: 4 },
  sectionTitle: { flex: 1, color: colors.ink, fontSize: 17, fontWeight: '900' as const },
  sectionAction: { flexDirection: 'row' as const, alignItems: 'center' as const, minHeight: 36 },
  sectionActionText: { color: colors.forest, fontSize: 12, fontWeight: '800' as const },
  statsRow: { flexDirection: 'row' as const, gap: 8 },
  stat: { flex: 1, minHeight: 76, borderRadius: radius.medium, borderWidth: 1, borderColor: colors.line, padding: 12, justifyContent: 'center' as const },
  statValue: { color: colors.ink, fontSize: 24, fontWeight: '900' as const },
  statLabel: { color: colors.inkSoft, fontSize: 11, marginTop: 3, fontWeight: '700' as const },
  recentRow: { gap: 10, paddingRight: 8 },
  recentItem: { width: 118 },
  recentImage: { width: 118, height: 92, borderRadius: radius.medium, backgroundColor: colors.line },
  recentTime: { color: colors.inkSoft, fontSize: 10, marginTop: 6 },
  recentName: { color: colors.ink, fontSize: 13, fontWeight: '800' as const, marginTop: 1 },
  emptyDiscovery: { flexDirection: 'row' as const, gap: 12, alignItems: 'center' as const, padding: 14, borderRadius: radius.medium, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white },
  emptyDiscoveryTitle: { color: colors.ink, fontSize: 14, fontWeight: '900' as const },
  emptyDiscoveryText: { color: colors.inkSoft, fontSize: 12, lineHeight: 17, marginTop: 3 },
  featuredRow: { gap: 10, paddingRight: 8 },
  featuredItem: { width: 190, borderRadius: radius.medium, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, overflow: 'hidden' as const },
  featuredImage: { width: '100%' as const, height: 128, backgroundColor: colors.line },
  featuredBody: { padding: 11 },
  featuredName: { color: colors.ink, fontSize: 15, fontWeight: '900' as const },
  featuredHint: { color: colors.inkSoft, fontSize: 11, lineHeight: 16, marginTop: 4 },
  actionTitle: { color: colors.ink, fontSize: 17, fontWeight: '900' as const, marginTop: 4 },
  actionRow: { flexDirection: 'row' as const, gap: 9 },
  quickAction: { flex: 1, minHeight: 76, borderRadius: radius.medium, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white, alignItems: 'center' as const, justifyContent: 'center' as const, gap: 7, ...shadow },
  quickActionLabel: { color: colors.ink, fontSize: 12, fontWeight: '800' as const },
  pressed: { opacity: 0.72 },
};
