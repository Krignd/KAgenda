# Reproducible schematic previews, using sample data only. Requires Pillow and a CJK font.
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
root=Path(__file__).resolve().parents[1];out=root/'design';out.mkdir(exist_ok=True)
res=root/'app/src/main/res';font='C:/Windows/Fonts/msyh.ttc'
def f(n):return ImageFont.truetype(font,n)
colors=['#3B82F6','#10B981','#8B5CF6','#F59E0B','#EF4444']
def draw_preview(kind, w=760,h=420, lang="zh"):
 im=Image.new('RGB',(w,h),'#F2F5F9');d=ImageDraw.Draw(im)
 d.rounded_rectangle((5,5,w-5,h-5),28,fill='white',outline='#E2E8F0',width=2)
 titles={'today_agenda':'今日日程','today_courses':'今日课表','week_agenda':'周日程','week_courses':'周课表'}
 if lang=='en':titles={'today_agenda':"Today’s agenda",'today_courses':"Today’s classes",'week_agenda':'Weekly agenda','week_courses':'Weekly classes'}
 if lang=='fr':titles={'today_agenda':'Agenda du jour','today_courses':'Cours du jour','week_agenda':'Agenda de la semaine','week_courses':'Cours de la semaine'}
 d.text((28,22),titles[kind],font=f(30),fill='#0F172A')
 weekly=kind.startswith('week');d.text((28,70),'2026-10-12 — 2026-10-18' if weekly else '2026-10-17',font=f(19),fill='#64748B')
 if kind=='week_courses':
  col=(w-40)/7
  names=['高等数学','大学英语','线性代数','实验课程','程序设计','课程讨论','']
  if lang=='en':names=['Maths','English','Algebra','Lab','Coding','Seminar','']
  if lang=='fr':names=['Maths','Anglais','Algèbre','TP','Code','Séminaire','']
  for i in range(7):
   x=20+i*col; d.text((x+8,111),({'zh':'一二三四五六日','en':'MTWTFSS','fr':'LMMJVSD'}[lang])[i],font=f(18),fill='#64748B')
   d.text((x+8,140),f'10/{12+i}',font=f(15),fill='#64748B')
   for k in range(2 if i<5 else (1 if i==5 else 0)):
    y=179+k*114;c=colors[(i+k)%5]
    d.rounded_rectangle((x+2,y,x+col-4,y+105),8,fill=c+'22' if False else ['#E8F0FF','#E4F6EF','#F1EBFC','#FFF5DF','#FDE9E9'][(i+k)%5])
    d.text((x+7,y+7),'08:00' if not k else '14:00',font=f(15),fill=c)
    title=names[(i+k)%len(names)] or {'zh':'学习交流','en':'Study','fr':'Étude'}[lang]
    d.text((x+7,y+31),title[:4],font=f(17),fill='#0F172A')
    if len(title)>4:d.text((x+7,y+53),title[4:10],font=f(17),fill='#0F172A')
    d.text((x+7,y+78),'A201',font=f(14),fill='#64748B')
 else:
  if kind=='today_agenda': rows=[('09:00','课程讨论','教学楼 A201'),('14:30','项目会议','图书馆 · 等待通知'),('19:00','讲座','报告厅')]
  elif kind=='today_courses':rows=[('08:00','高等数学','教学楼 A201'),('10:00','线性代数','教学楼 B302'),('14:00','大学英语','教学楼 C105')]
  else: rows=[('10/12','周一',''),('08:00','高等数学','教学楼 A201'),('14:30','项目会议','图书馆'),('10/13','周二','')]
  if lang!='zh':
   words={'高等数学':('Mathematics','Mathématiques'),'线性代数':('Algebra','Algèbre'),'大学英语':('English','Anglais'),'课程讨论':('Seminar','Séminaire'),'项目会议':('Project meeting','Réunion de projet'),'讲座':('Lecture','Conférence'),'周一':('Monday','Lundi'),'周二':('Tuesday','Mardi'),'教学楼 A201':('Building A201','Bâtiment A201'),'教学楼 B302':('Building B302','Bâtiment B302'),'教学楼 C105':('Building C105','Bâtiment C105'),'图书馆':('Library','Bibliothèque'),'图书馆 · 等待通知':('Library · Awaiting notice','Bibliothèque · En attente'),'报告厅':('Lecture hall','Amphithéâtre')}
   idx=0 if lang=='en' else 1
   rows=[(tm,words.get(title,(title,title))[idx],words.get(loc,(loc,loc))[idx]) for tm,title,loc in rows]
  for i,(tm,title,loc) in enumerate(rows):
   y=121+i*(76 if kind!='week_agenda' else 64);c=colors[i%5]
   d.text((28,y),tm,font=f(23),fill=c)
   d.text((151,y-1),title,font=f(25),fill='#0F172A')
   d.text((151,y+34),loc,font=f(18),fill='#64748B')
 d.text((28,h-31),{'zh':'点击查看全部','en':'Tap to view all','fr':'Touchez pour tout voir'}[lang],font=f(16),fill='#64748B')
 return im
previews={}
for kind in ['today_agenda','today_courses','week_agenda','week_courses']:
 im=draw_preview(kind,h=510 if kind.startswith('week') else 420);previews[kind]=im
 for lang,folder in [('en','drawable-nodpi'),('zh','drawable-zh-nodpi'),('fr','drawable-fr-nodpi')]:
  target=res/folder;target.mkdir(exist_ok=True)
  draw_preview(kind,h=510 if kind.startswith('week') else 420,lang=lang).save(target/f'widget_preview_{kind}.png')
poster=Image.new('RGB',(1660,1320),'#F2F5F9');d=ImageDraw.Draw(poster)
d.text((50,30),'K日程 · 新增小组件示意图',font=f(44),fill='#0F172A')
d.text((52,96),'今日：4×2（可缩至 2×2）    周：4×4（可扩展至 5×4 等尺寸）',font=f(24),fill='#64748B')
for kind,x,y in [('today_agenda',40,160),('today_courses',860,160),('week_agenda',40,660),('week_courses',860,660)]:poster.paste(previews[kind],(x,y))
d.text((52,1230),'示例数据；日程列表含课程与自建日程，课表只显示课程。',font=f(24),fill='#64748B')
d.text((52,1270),'可滚动查看完整内容；实际显示随桌面尺寸和系统字体调整。',font=f(22),fill='#64748B')
poster.save(out/'widget-previews.png')

# 原有「下一项」四种尺寸：保持当前课程主次、下一项省略、示例不使用个人数据。
def draw_legacy(kind,lang='zh'):
 w=760 if kind.startswith('4') else 380
 h=420 if kind.endswith('2') else 210
 im=Image.new('RGB',(w,h),'#F2F5F9');d=ImageDraw.Draw(im)
 d.rounded_rectangle((5,5,w-5,h-5),24,fill='white',outline='#E2E8F0',width=2)
 d.rounded_rectangle((22,27,29,h-27),3,fill='#3B82F6')
 words={'zh':('高等数学','王老师','进行中 · 还剩20分钟','下一节 线性代数 10:00'),
        'en':('Mathematics','Prof. Wang','Ongoing · 20 min left','Next: Algebra 10:00'),
        'fr':('Mathématiques','Pr Wang','En cours · reste 20 min','Puis : Algèbre 10:00')}[lang]
 name,teacher,remaining,nxt=words
 compact=kind=='4x1'
 x=50;y=31
 if kind.endswith('2'):
  d.text((x,y),'2026-10-17',font=f(20),fill='#64748B');y+=42
 d.rectangle((x,y+7,x+22,y+30),outline='#0F172A',width=2)
 d.line((x+11,y+7,x+11,y+30),fill='#0F172A',width=2)
 d.text((x+34,y),name,font=f(28),fill='#0F172A');y+=43
 d.text((x,y),'08:00–09:35',font=f(22),fill='#64748B');y+=32
 d.text((x,y),'A201',font=f(21),fill='#64748B');y+=30
 if kind.endswith('2'):
  d.text((x,y),teacher,font=f(21),fill='#64748B');y+=43
 if compact:
  d.text((470,57),remaining,font=f(20),fill='#1D4ED8')
  d.text((470,98),nxt,font=f(18),fill='#64748B')
 else:
  d.text((x,y),remaining,font=f(21),fill='#1D4ED8');y+=35
  if kind.endswith('2'):d.text((x,y),nxt,font=f(18),fill='#64748B')
 return im

for kind in ['2x1','2x2','4x1','4x2']:
 for lang,folder in [('en','drawable-nodpi'),('zh','drawable-zh-nodpi'),('fr','drawable-fr-nodpi')]:
  draw_legacy(kind,lang).save(res/folder/f'widget_preview_{kind}.png')
legacy=Image.new('RGB',(1660,1150),'#F2F5F9');d=ImageDraw.Draw(legacy)
d.text((50,30),'K日程 · 原有小组件示意图',font=f(44),fill='#0F172A')
d.text((50,100),'示例数据 · 当前课程优先显示 · 具体尺寸随桌面调整',font=f(24),fill='#64748B')
for kind,x,y in [('2x1',50,190),('4x1',650,190),('2x2',50,550),('4x2',650,550)]:
 d.text((x,y-42),kind.replace('x','×'),font=f(28),fill='#475569')
 legacy.paste(draw_legacy(kind),(x,y))
legacy.save(out/'legacy-widget-previews.png')
combined=Image.new('RGB',(1660,2470),'#F2F5F9')
combined.paste(legacy,(0,0));combined.paste(poster,(0,1150))
combined.save(out/'widget-previews.png')
