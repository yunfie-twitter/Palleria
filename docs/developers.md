---
layout: page
title: 開発者
description: Palleria の開発者紹介。
---

<script setup>
import {
  VPTeamPage,
  VPTeamPageTitle,
  VPTeamMembers
} from 'vitepress/theme'

const homeIcon = {
  svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10.8 12 3l9 7.8v9.7a.5.5 0 0 1-.5.5h-5.25v-6.25h-6.5V21H3.5a.5.5 0 0 1-.5-.5v-9.7Zm2 0V19h1.75v-6.25h10.5V19H19v-8.2L12 4.72 5 10.8Z" fill="currentColor"/></svg>'
}

const members = [
  {
    avatar: 'https://yunfi.f5.si/Palleria/icons.jpg',
    name: 'ゆんふぃ',
    title: 'Developer',
    links: [
      { icon: 'github', link: 'https://github.com/yunfie-twitter' },
      { icon: 'twitter', link: 'https://x.com/yunfie_misskey' },
      { icon: homeIcon, link: 'https://yunfi.f5.si/', ariaLabel: 'ゆんふぃのホームページ' }
    ]
  }
]
</script>

<VPTeamPage>
  <VPTeamPageTitle>
    <template #title>開発者</template>
    <template #lead>
      Palleria を開発・保守しているメンバーです。
    </template>
  </VPTeamPageTitle>

  <VPTeamMembers size="medium" :members="members" />
</VPTeamPage>
