import {
  ArrowLeft,
  CalendarDays,
  ChevronRight,
  MapPin,
  Search,
  Trash2,
} from 'lucide-react-native';
import { useMemo, useState } from 'react';
import { Image, Pressable, ScrollView, Text, TextInput, View } from 'react-native';

import { speciesCandidates } from '../data/kamiyama';
import { featuredSpeciesIds, speciesImages } from '../data/speciesImages';
import { colors, radius, shadow } from '../styles/theme';
import type { Observation, Rarity, SpeciesCategory } from '../types/domain';

type EncyclopediaPanelProps = {
  observations: Observation[];
  onDeleteObservation: (id: string) => void;
};

type Filter = 'all' | SpeciesCategory;

const rarityLabel: Record<Rarity, string> = {
  common: 'よく見つかる',
  uncommon: 'ちょっと珍しい',
  rare: 'レア',
  special: 'とくべつ',
};

const rarityStyle: Record<Rarity, { backgroundColor: string; color: string }> = {
  common: { backgroundColor: colors.leafPale, color: colors.forest },
  uncommon: { backgroundColor: colors.bluePale, color: colors.blue },
  rare: { backgroundColor: colors.amberPale, color: '#9A6712' },
  special: { backgroundColor: '#FCE3DD', color: colors.danger },
};

export function EncyclopediaPanel({ observations, onDeleteObservation }: EncyclopediaPanelProps) {
  const [filter, setFilter] = useState<Filter>('all');
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<Observation | null>(null);

  const filtered = useMemo(() => observations.filter((item) => {
    if (filter !== 'all' && item.category !== filter) return false;
    const needle = query.trim().toLowerCase();
    if (!needle) return true;
    return `${item.customName} ${item.environment}`.toLowerCase().includes(needle);
  }), [filter, observations, query]);

  if (selected) {
    return (
      <ObservationDetail
        observation={selected}
        onBack={() => setSelected(null)}
        onDelete={async () => {
          await onDeleteObservation(selected.id);
          setSelected(null);
        }}
      />
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
      <View style={styles.introRow}>
        <View style={{ flex: 1 }}>
          <Text style={styles.heading}>見つけた生き物</Text>
          <Text style={styles.subheading}>{observations.length}件の観察記録</Text>
        </View>
        <View style={styles.countBadge}>
          <Text style={styles.countValue}>{new Set(observations.map((item) => item.candidateId).filter(Boolean)).size}</Text>
          <Text style={styles.countLabel}>種類</Text>
        </View>
      </View>

      <View style={styles.searchBox}>
        <Search color={colors.inkSoft} size={19} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="いきものの名前で検索"
          placeholderTextColor="#809087"
          style={styles.searchInput}
        />
      </View>

      <View style={styles.filterRow}>
        <FilterChip label="すべて" active={filter === 'all'} onPress={() => setFilter('all')} />
        <FilterChip label="植物" active={filter === 'plant'} onPress={() => setFilter('plant')} />
        <FilterChip label="昆虫" active={filter === 'insect'} onPress={() => setFilter('insect')} />
      </View>

      {filtered.length === 0 ? (
        <View style={styles.emptyState}>
          <View style={styles.emptyPhotoRow}>
            {featuredSpeciesIds.slice(0, 3).map((id) => (
              <Image key={id} source={speciesImages[id]} style={styles.emptyPhoto} />
            ))}
          </View>
          <Text style={styles.emptyTitle}>{observations.length === 0 ? '図鑑の最初のページを作ろう' : '見つかりませんでした'}</Text>
          <Text style={styles.emptyText}>
            {observations.length === 0
              ? 'THINKLETで植物や昆虫を撮ると、AI候補を確認したあとにここへ登録されます。'
              : '検索する名前やカテゴリーを変えてみてください。'}
          </Text>
        </View>
      ) : null}

      <View style={styles.list}>
        {filtered.map((observation) => {
          const candidate = speciesCandidates.find((item) => item.id === observation.candidateId);
          return (
            <Pressable
              key={observation.id}
              onPress={() => setSelected(observation)}
              style={({ pressed }) => [styles.row, pressed && styles.pressed]}
            >
              <Image source={{ uri: observation.photoUri }} style={styles.rowImage} />
              <View style={styles.rowBody}>
                <View style={styles.rowTitleLine}>
                  <Text style={styles.rowTitle} numberOfLines={1}>{observation.customName}</Text>
                  <Text style={[styles.rarity, rarityStyle[observation.rarity]]}>{rarityLabel[observation.rarity]}</Text>
                </View>
                <Text style={styles.scientific} numberOfLines={1}>{candidate?.scientificName ?? '名前を確認中'}</Text>
                <View style={styles.metaLine}>
                  <CalendarDays color={colors.inkSoft} size={13} />
                  <Text style={styles.metaText}>{formatDate(observation.observedAt)}</Text>
                </View>
                <View style={styles.metaLine}>
                  <MapPin color={colors.inkSoft} size={13} />
                  <Text style={styles.metaText} numberOfLines={1}>{observation.environment}</Text>
                </View>
              </View>
              <ChevronRight color={colors.lineStrong} size={21} />
            </Pressable>
          );
        })}
      </View>
    </ScrollView>
  );
}

function ObservationDetail({ observation, onBack, onDelete }: { observation: Observation; onBack: () => void; onDelete: () => void }) {
  const candidate = speciesCandidates.find((item) => item.id === observation.candidateId);
  return (
    <ScrollView contentContainerStyle={styles.detailContent} showsVerticalScrollIndicator={false}>
      <View style={styles.detailToolbar}>
        <Pressable onPress={onBack} style={styles.roundIconButton} accessibilityLabel="図鑑へ戻る">
          <ArrowLeft color={colors.ink} size={22} />
        </Pressable>
        <Text style={styles.detailToolbarTitle}>観察記録</Text>
        <Pressable onPress={onDelete} style={styles.roundIconButton} accessibilityLabel="この記録を削除">
          <Trash2 color={colors.danger} size={20} />
        </Pressable>
      </View>

      <View style={styles.heroImageWrap}>
        <Image source={{ uri: observation.photoUri }} style={styles.heroImage} />
        {observation.source === 'thinklet' ? (
          <View style={styles.thinkletBadge}><Text style={styles.thinkletBadgeText}>THINKLETで撮影</Text></View>
        ) : null}
      </View>

      <View style={styles.detailHeader}>
        <View style={{ flex: 1 }}>
          <Text style={styles.detailCategory}>{observation.category === 'plant' ? '植物' : '昆虫'}</Text>
          <Text style={styles.detailTitle}>{observation.customName}</Text>
          <Text style={styles.detailScientific}>{candidate?.scientificName ?? observation.aiScientificName ?? '学名を確認中'}</Text>
        </View>
        <Text style={[styles.rarity, styles.detailRarity, rarityStyle[observation.rarity]]}>{rarityLabel[observation.rarity]}</Text>
      </View>

      <View style={styles.detailMeta}>
        <View style={styles.detailMetaItem}>
          <CalendarDays color={colors.forest} size={19} />
          <View><Text style={styles.detailMetaLabel}>見つけた日</Text><Text style={styles.detailMetaValue}>{formatDateTime(observation.observedAt)}</Text></View>
        </View>
        <View style={styles.detailMetaDivider} />
        <View style={styles.detailMetaItem}>
          <MapPin color={colors.forest} size={19} />
          <View style={{ flex: 1 }}><Text style={styles.detailMetaLabel}>見つけた場所</Text><Text style={styles.detailMetaValue} numberOfLines={1}>{observation.environment}</Text></View>
        </View>
      </View>

      <DetailSection title="特徴" text={candidate?.hint ?? observation.aiReason ?? 'この観察の特徴を記録しています。'} />
      {observation.note ? <DetailSection title="観察メモ" text={observation.note} /> : null}
      <DetailSection
        title="記録地点"
        text={`${observation.latitude.toFixed(4)}, ${observation.longitude.toFixed(4)}${observation.accuracy ? `（精度 約${Math.round(observation.accuracy)}m）` : ''}`}
      />
    </ScrollView>
  );
}

function DetailSection({ title, text }: { title: string; text: string }) {
  return <View style={styles.detailSection}><Text style={styles.detailSectionTitle}>{title}</Text><Text style={styles.detailSectionText}>{text}</Text></View>;
}

function FilterChip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.filterChip, active && styles.filterChipActive]}>
      <Text style={[styles.filterText, active && styles.filterTextActive]}>{label}</Text>
    </Pressable>
  );
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit' });
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ja-JP', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

const styles = {
  scrollContent: { padding: 18, paddingBottom: 112, gap: 14 },
  introRow: { flexDirection: 'row' as const, alignItems: 'center' as const },
  heading: { color: colors.ink, fontSize: 22, fontWeight: '900' as const },
  subheading: { color: colors.inkSoft, fontSize: 12, marginTop: 4 },
  countBadge: { width: 56, height: 56, borderRadius: radius.medium, alignItems: 'center' as const, justifyContent: 'center' as const, backgroundColor: colors.leafPale },
  countValue: { color: colors.forestDark, fontSize: 20, lineHeight: 22, fontWeight: '900' as const },
  countLabel: { color: colors.inkSoft, fontSize: 10, fontWeight: '700' as const },
  searchBox: { height: 48, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 9, paddingHorizontal: 14, borderRadius: radius.medium, borderWidth: 1, borderColor: colors.lineStrong, backgroundColor: colors.white },
  searchInput: { flex: 1, height: 46, padding: 0, color: colors.ink, fontSize: 14 },
  filterRow: { flexDirection: 'row' as const, gap: 8 },
  filterChip: { minHeight: 38, paddingHorizontal: 17, borderRadius: 20, alignItems: 'center' as const, justifyContent: 'center' as const, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line },
  filterChipActive: { backgroundColor: colors.forest, borderColor: colors.forest },
  filterText: { color: colors.inkSoft, fontSize: 12, fontWeight: '800' as const },
  filterTextActive: { color: colors.white },
  list: { gap: 9 },
  row: { minHeight: 118, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 12, padding: 9, borderRadius: radius.medium, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, ...shadow },
  rowImage: { width: 104, height: 98, borderRadius: radius.small, backgroundColor: colors.line },
  rowBody: { flex: 1, gap: 4 },
  rowTitleLine: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 7 },
  rowTitle: { flex: 1, color: colors.ink, fontSize: 16, fontWeight: '900' as const },
  scientific: { color: colors.inkSoft, fontSize: 10, fontStyle: 'italic' as const, marginBottom: 2 },
  rarity: { overflow: 'hidden' as const, borderRadius: 6, paddingHorizontal: 7, paddingVertical: 4, fontSize: 9, fontWeight: '900' as const },
  metaLine: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 5 },
  metaText: { color: colors.inkSoft, fontSize: 11, flexShrink: 1 },
  emptyState: { borderRadius: radius.medium, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white, padding: 16, alignItems: 'center' as const },
  emptyPhotoRow: { flexDirection: 'row' as const, marginBottom: 15 },
  emptyPhoto: { width: 70, height: 70, borderRadius: 35, borderWidth: 3, borderColor: colors.white, marginHorizontal: -6, backgroundColor: colors.line },
  emptyTitle: { color: colors.ink, fontSize: 17, fontWeight: '900' as const },
  emptyText: { color: colors.inkSoft, fontSize: 12, lineHeight: 19, textAlign: 'center' as const, maxWidth: 360, marginTop: 7 },
  detailContent: { paddingBottom: 112 },
  detailToolbar: { minHeight: 58, paddingHorizontal: 14, flexDirection: 'row' as const, alignItems: 'center' as const },
  roundIconButton: { width: 40, height: 40, borderRadius: 8, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, alignItems: 'center' as const, justifyContent: 'center' as const },
  detailToolbarTitle: { flex: 1, textAlign: 'center' as const, color: colors.ink, fontSize: 15, fontWeight: '900' as const },
  heroImageWrap: { marginHorizontal: 14, position: 'relative' as const },
  heroImage: { width: '100%' as const, aspectRatio: 1.25, borderRadius: radius.medium, backgroundColor: colors.line },
  thinkletBadge: { position: 'absolute' as const, right: 10, top: 10, borderRadius: 6, paddingHorizontal: 10, paddingVertical: 7, backgroundColor: 'rgba(35, 91, 57, 0.88)' },
  thinkletBadgeText: { color: colors.white, fontSize: 10, fontWeight: '900' as const },
  detailHeader: { padding: 18, flexDirection: 'row' as const, alignItems: 'flex-start' as const, gap: 10 },
  detailCategory: { color: colors.forest, fontSize: 11, fontWeight: '900' as const },
  detailTitle: { color: colors.ink, fontSize: 26, fontWeight: '900' as const, marginTop: 2 },
  detailScientific: { color: colors.inkSoft, fontSize: 12, fontStyle: 'italic' as const, marginTop: 3 },
  detailRarity: { marginTop: 4 },
  detailMeta: { marginHorizontal: 18, paddingVertical: 13, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.line, flexDirection: 'row' as const, alignItems: 'center' as const },
  detailMetaItem: { flex: 1, minWidth: 0, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 9 },
  detailMetaDivider: { width: 1, height: 38, backgroundColor: colors.line, marginHorizontal: 12 },
  detailMetaLabel: { color: colors.inkSoft, fontSize: 9, fontWeight: '700' as const },
  detailMetaValue: { color: colors.ink, fontSize: 11, fontWeight: '800' as const, marginTop: 2 },
  detailSection: { marginHorizontal: 18, paddingVertical: 16, borderBottomWidth: 1, borderBottomColor: colors.line },
  detailSectionTitle: { color: colors.ink, fontSize: 15, fontWeight: '900' as const },
  detailSectionText: { color: colors.inkSoft, fontSize: 13, lineHeight: 21, marginTop: 7 },
  pressed: { opacity: 0.7 },
};
