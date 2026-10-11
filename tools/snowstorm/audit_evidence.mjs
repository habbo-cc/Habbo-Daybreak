import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { basename, extname, join, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const SERVER_ROOT = resolve(import.meta.dirname, '..', '..');
const EVIDENCE_ROOT = join(import.meta.dirname, 'evidence');
const HABBO_ROOT = 'C:/habbo';
const R26_ROOT = join(HABBO_ROOT, 'client/v14/dcr/r26_20080915_0408_7984_61ccb5f8b8797a3aba62c1fa2ca80169');
const B8_ROOT = join(HABBO_ROOT, 'client/v14/dcr/14.1_b8');
const IGOR_ROOT = join(HABBO_ROOT, 'hh_ig_game_snowwar');
const HAVANA_ROOT = join(HABBO_ROOT, 'server/Havana');
const QUACKSTER_ROOT = join(HABBO_ROOT, 'snowstorm');
const EXTRACTOR = join(HABBO_ROOT, 'ccttoswf/convert_snowstorm.py');
const PROJECTORRAYS_PACKAGE = join(
    HABBO_ROOT,
    'ccttoswf/.tools/projectorrays/node_modules/projectorrays/dist/pkg/node.es.js'
);
const CAST_NAMES = ['hh_game_snowwar.cct', 'hh_game_snowwar_room.cct', 'hh_game_snowwar_ui.cct'];
const SHARED_CAST_NAMES = ['hh_gamesys.cct', 'hh_games.cct'];

function fail(message) {
    throw new Error(message);
}

function sha256Bytes(bytes) {
    return createHash('sha256').update(bytes).digest('hex').toUpperCase();
}

function sha256File(path) {
    return sha256Bytes(readFileSync(path));
}

function allFiles(root, predicate = () => true) {
    const result = [];
    function visit(path) {
        for (const name of readdirSync(path).sort((a, b) => a.localeCompare(b))) {
            const child = join(path, name);
            const stat = statSync(child);
            if (stat.isDirectory()) visit(child);
            else if (predicate(child)) result.push(child);
        }
    }
    visit(root);
    return result;
}

function treeManifest(root, predicate = () => true) {
    const files = allFiles(root, predicate).map(path => ({
        path: relative(root, path).replaceAll('\\', '/'),
        sha256: sha256File(path),
        bytes: statSync(path).size
    }));
    const canonical = files.map(file => file.path + '\u0000' + file.sha256 + '\n').join('');
    return { root: root.replaceAll('\\', '/'), fileCount: files.length, sha256: sha256Bytes(canonical), files };
}

function git(root, args) {
    return execFileSync('git', ['-C', root, ...args], { encoding: 'utf8' }).trim();
}

function gitSource(root, license, role) {
    return {
        path: root.replaceAll('\\', '/'),
        revision: git(root, ['rev-parse', 'HEAD']),
        remote: git(root, ['remote', 'get-url', 'origin']),
        dirty: git(root, ['status', '--porcelain=v1']) !== '',
        license,
        role
    };
}

function castSource(root, filename, version, role) {
    const path = join(root, filename);
    if (!existsSync(path)) fail('Missing cast: ' + path);
    return {
        path: path.replaceAll('\\', '/'),
        filename,
        version,
        sha256: sha256File(path),
        bytes: statSync(path).size,
        license: 'No redistribution licence present in the archive; preservation/reference evidence only',
        role
    };
}

function chunkCounts(chunks) {
    const result = {};
    for (const chunk of chunks) result[chunk.fourCC] = (result[chunk.fourCC] || 0) + 1;
    return Object.fromEntries(Object.entries(result).sort(([a], [b]) => a.localeCompare(b)));
}

function scriptEntries(dump) {
    const entries = [];
    for (const cast of dump.casts || []) {
        for (const script of cast.scripts || []) {
            entries.push({
                cast: cast.name,
                scriptId: script.scriptId,
                memberId: script.memberId,
                memberName: script.memberName,
                scriptType: script.scriptType,
                lingoSha256: sha256Bytes(script.lingo || ''),
                lingo: script.lingo || ''
            });
        }
    }
    return entries.sort((a, b) => a.scriptId - b.scriptId || a.memberId - b.memberId);
}

function memberEntries(json, chunks) {
    const key = json.find(entry => entry.fourCC === 'KEY*');
    const relationships = new Map();
    for (const entry of key?.data?.entries || []) {
        if (!entry.castID || !entry.sectionID || !entry.fourCC.trim()) continue;
        const list = relationships.get(entry.castID) || [];
        list.push({ fourCC: entry.fourCC, id: entry.sectionID });
        relationships.set(entry.castID, list);
    }
    const chunkIndex = new Map(chunks.map(chunk => [chunk.fourCC + ':' + chunk.id, chunk]));
    const members = [];
    for (const entry of json.filter(value => value.fourCC === 'CASt')) {
        const info = entry.data?.info || {};
        const children = (relationships.get(entry.id) || [])
            .filter(child => child.fourCC !== 'Thum')
            .sort((a, b) => a.fourCC.localeCompare(b.fourCC) || a.id - b.id)
            .map(child => {
                const chunk = chunkIndex.get(child.fourCC + ':' + child.id);
                return {
                    fourCC: child.fourCC,
                    id: child.id,
                    sha256: chunk ? sha256Bytes(chunk.data) : null,
                    bytes: chunk?.data?.length ?? null
                };
            });
        members.push({
            id: entry.id,
            name: info.name || '',
            type: entry.data?.type ?? null,
            scriptId: info.scriptId || 0,
            children
        });
    }
    return members.sort((a, b) => a.id - b.id);
}

function namesIndex(members) {
    const result = new Map();
    for (const member of members) {
        const key = member.name;
        const list = result.get(key) || [];
        list.push(member);
        result.set(key, list);
    }
    return result;
}

function memberSignature(member) {
    return JSON.stringify({
        type: member.type,
        children: member.children.map(child => ({
            fourCC: child.fourCC,
            sha256: child.sha256,
            bytes: child.bytes
        }))
    });
}

function compareMembers(r26, b8) {
    const rIndex = namesIndex(r26);
    const bIndex = namesIndex(b8);
    const allNames = [...new Set([...rIndex.keys(), ...bIndex.keys()])].sort((a, b) => a.localeCompare(b));
    const addedInR26 = [];
    const removedAfterB8 = [];
    const changed = [];
    const unchanged = [];
    for (const name of allNames) {
        const newer = rIndex.get(name) || [];
        const older = bIndex.get(name) || [];
        if (!older.length) addedInR26.push({ name, members: newer });
        else if (!newer.length) removedAfterB8.push({ name, members: older });
        else {
            const newerSignatures = newer.map(memberSignature).sort();
            const olderSignatures = older.map(memberSignature).sort();
            const record = {
                name,
                r26Ids: newer.map(member => member.id),
                b8Ids: older.map(member => member.id),
                r26Types: newer.map(member => member.type),
                b8Types: older.map(member => member.type)
            };
            if (JSON.stringify(newerSignatures) === JSON.stringify(olderSignatures)) unchanged.push(record);
            else changed.push(record);
        }
    }
    return { addedInR26, removedAfterB8, changed, unchanged };
}

function normalizeScriptName(path) {
    return basename(path, extname(path)).replace(/^\d+/, '').trim().toLowerCase();
}

function matchRecoveredScripts(r26Scripts) {
    const rawFiles = allFiles(join(IGOR_ROOT, 'disasm'), path => extname(path).toLowerCase() === '.txt');
    const recoveredFiles = allFiles(join(IGOR_ROOT, 'disasm'), path => extname(path).toLowerCase() === '.ls');
    const cleanedFiles = allFiles(join(IGOR_ROOT, 'scripts'), path => extname(path).toLowerCase() === '.ls');
    const byName = files => new Map(files.map(path => [normalizeScriptName(path), path]));
    const raw = byName(rawFiles);
    const recovered = byName(recoveredFiles);
    const cleaned = byName(cleanedFiles);
    return r26Scripts.map(script => {
        const key = script.memberName.trim().toLowerCase();
        const rawPath = raw.get(key);
        const recoveredPath = recovered.get(key);
        const cleanedPath = cleaned.get(key);
        return {
            scriptId: script.scriptId,
            memberId: script.memberId,
            memberName: script.memberName,
            scriptType: script.scriptType,
            projectorraysLingoSha256: script.lingoSha256,
            rawDisassembly: rawPath ? relative(IGOR_ROOT, rawPath).replaceAll('\\', '/') : null,
            rawDisassemblySha256: rawPath ? sha256File(rawPath) : null,
            recoveredLingo: recoveredPath ? relative(IGOR_ROOT, recoveredPath).replaceAll('\\', '/') : null,
            recoveredLingoSha256: recoveredPath ? sha256File(recoveredPath) : null,
            cleanedLingo: cleanedPath ? relative(IGOR_ROOT, cleanedPath).replaceAll('\\', '/') : null,
            cleanedLingoSha256: cleanedPath ? sha256File(cleanedPath) : null
        };
    });
}

function markdownComparison(report) {
    const lines = [
        '# SnowStorm r26 versus 14.1 b8 cast audit',
        '',
        'Generated by tools/snowstorm/audit_evidence.mjs. r26 remains the target; 14.1 b8 is comparison only.',
        '',
        '| Library | r26 members | 14.1 members | Added in r26 | Removed since 14.1 | Changed payload/type | Same payload/type |',
        '|---|---:|---:|---:|---:|---:|---:|'
    ];
    for (const library of report.libraries) {
        lines.push(
            '| ' + library.library + ' | ' + library.r26.memberCount + ' | ' + library.b8.memberCount +
            ' | ' + library.diff.addedInR26.length + ' | ' + library.diff.removedAfterB8.length +
            ' | ' + library.diff.changed.length + ' | ' + library.diff.unchanged.length + ' |'
        );
    }
    lines.push('', '## Script mapping', '');
    const core = report.libraries.find(value => value.library === 'hh_game_snowwar');
    lines.push(
        '- r26 scripts: ' + core.r26.scriptCount,
        '- 14.1 b8 scripts: ' + core.b8.scriptCount,
        '- Igor raw disassemblies matched: ' + report.scriptMapping.filter(value => value.rawDisassembly).length,
        '- Igor recovered Lingo files matched: ' + report.scriptMapping.filter(value => value.recoveredLingo).length,
        '- Optional cleaned scripts matched: ' + report.scriptMapping.filter(value => value.cleanedLingo).length,
        '',
        'The full member records, payload hashes and script-to-ID mapping are in cast_comparison.json.',
        '',
        '## Selection rule',
        '',
        'No 14.1-only or changed member may enter runtime assets or behavior without a behavior-matrix row proving why r26 requires it. Differences are evidence to investigate, not an import list.',
        ''
    );
    return lines.join('\n');
}

if (!existsSync(PROJECTORRAYS_PACKAGE)) {
    fail('Install ProjectorRays 1.1.1 under C:/habbo/ccttoswf/.tools/projectorrays first');
}
mkdirSync(EVIDENCE_ROOT, { recursive: true });
const { DirectorFile } = await import(pathToFileURL(PROJECTORRAYS_PACKAGE).href);

const sourceManifest = {
    schemaVersion: 1,
    target: 'Habbo Shockwave r26 SnowStorm',
    evidencePrecedence: [
        'r26 casts and recovered raw bytecode',
        'cleaned Lingo matched to raw bytecode',
        '14.1 b8 comparison casts',
        'Havana only where independently corroborated',
        'Quackster guide as an index/explanation',
        'later AIR snowwar is excluded as gameplay truth'
    ],
    casts: [
        ...CAST_NAMES.map(name => castSource(R26_ROOT, name, 'r26', 'authoritative target')),
        ...CAST_NAMES.map(name => castSource(B8_ROOT, name, '14.1_b8', 'historical comparison only')),
        ...SHARED_CAST_NAMES.map(name => castSource(
            R26_ROOT,
            name,
            'r26',
            'authoritative shared game runtime used by the target SnowStorm casts'
        ))
    ],
    recoveredClient: gitSource(IGOR_ROOT, 'GPL-3.0', 'raw and cleaned r26 Lingo recovery'),
    explanatoryGuide: gitSource(QUACKSTER_ROOT, 'Apache-2.0', 'explanation/index only'),
    havana: {
        path: HAVANA_ROOT.replaceAll('\\', '/'),
        revision: null,
        dirty: null,
        license: 'AGPL-3.0',
        role: 'candidate server/map comparison only; local snapshot has no .git metadata',
        snowstormSourceTree: treeManifest(
            join(HAVANA_ROOT, 'Havana-Server/src/main/java/org/alexdev/havana/game/games/snowstorm')
        ),
        mapTree: treeManifest(join(HAVANA_ROOT, 'tools/snowstorm_maps'))
    },
    extractor: {
        path: EXTRACTOR.replaceAll('\\', '/'),
        sha256: sha256File(EXTRACTOR),
        role: 'pinned r26 cast extractor'
    },
    projectorRays: {
        version: '1.1.1',
        license: 'MPL-2.0',
        package: 'https://www.npmjs.com/package/projectorrays',
        role: 'cross-version RIFX/XFIR evidence parser only'
    },
    battleBallGuidance: {
        server: ['5e3d61b0d6cb038df48b21eb23efbdbe399759cb', 'f96376d6b9561dda8558d2545595163652c7e340'],
        client: ['999c4483a5846a6668de0781547d0cd4db53ad3c', '9b265927b2779d0756cdf4821af2098cdfa6c27f'],
        role: 'integration patterns only'
    }
};
writeFileSync(join(EVIDENCE_ROOT, 'source_manifest.json'), JSON.stringify(sourceManifest, null, 2) + '\n');

const comparison = { schemaVersion: 1, libraries: [], scriptMapping: [] };
for (const filename of CAST_NAMES) {
    const library = basename(filename, '.cct');
    const files = { r26: join(R26_ROOT, filename), b8: join(B8_ROOT, filename) };
    const versions = {};
    for (const [version, path] of Object.entries(files)) {
        const file = await DirectorFile.readFromPath(path);
        try {
            const chunks = file.dumpChunks();
            const json = file.dumpJSON();
            const scripts = scriptEntries(file.dumpScripts());
            const members = memberEntries(json, chunks);
            versions[version] = {
                sourceSha256: sha256File(path),
                chunkCount: chunks.length,
                chunkCounts: chunkCounts(chunks),
                memberCount: members.length,
                scriptCount: scripts.length,
                members,
                scripts: scripts.map(({ lingo, ...record }) => record)
            };
            if (version === 'r26' && library === 'hh_game_snowwar') {
                comparison.scriptMapping = matchRecoveredScripts(scripts);
            }
        } finally {
            file.destroy();
        }
    }
    comparison.libraries.push({
        library,
        r26: versions.r26,
        b8: versions.b8,
        diff: compareMembers(versions.r26.members, versions.b8.members)
    });
}
writeFileSync(join(EVIDENCE_ROOT, 'cast_comparison.json'), JSON.stringify(comparison, null, 2) + '\n');
writeFileSync(join(EVIDENCE_ROOT, 'cast_comparison.md'), markdownComparison(comparison));

const sharedRuntime = { schemaVersion: 1, casts: [] };
for (const filename of SHARED_CAST_NAMES) {
    const path = join(R26_ROOT, filename);
    const file = await DirectorFile.readFromPath(path);
    try {
        const scripts = scriptEntries(file.dumpScripts());
        sharedRuntime.casts.push({
            filename,
            sourceSha256: sha256File(path),
            scriptCount: scripts.length,
            scripts
        });
    } finally {
        file.destroy();
    }
}
writeFileSync(
    join(EVIDENCE_ROOT, 'shared_gamesystem_scripts.json'),
    JSON.stringify(sharedRuntime, null, 2) + '\n'
);

const unmatched = comparison.scriptMapping.filter(
    record => !record.rawDisassembly || !record.recoveredLingo
);
if (comparison.scriptMapping.length !== 29 || unmatched.length) {
    fail('Expected all 29 r26 scripts to match Igor raw/recovered files; unmatched=' + unmatched.length);
}
console.log('Evidence audit complete:');
console.log('  source_manifest.json');
console.log('  cast_comparison.json');
console.log('  cast_comparison.md');
console.log('  shared_gamesystem_scripts.json');
console.log('  scripts matched: ' + comparison.scriptMapping.length + '/29');
