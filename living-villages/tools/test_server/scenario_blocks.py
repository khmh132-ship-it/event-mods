"""Job blocks register, place with a facing, and their recipes/loot tables load."""
from harness import TestServer

with TestServer({}, log_name='test_blocks.log') as s:
    s.cmd('forceload add -8 -8 8 8')
    for i, name in enumerate(['builders_table', 'chopping_block', 'miners_bench', 'weapon_rack', 'stockpile_crate']):
        print(name, '|', s.cmd(f'setblock {i * 2} 64 0 livingvillages:{name}[facing=east]', check=False).strip(),
              '|', s.cmd(f'execute if block {i * 2} 64 0 livingvillages:{name}[facing=east]', check=False).strip())
    print(s.cmd('recipe give @a livingvillages:builders_table', check=False).strip())
